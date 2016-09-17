/*
 * This file is part of LanternServer, licensed under the MIT License (MIT).
 *
 * Copyright (c) LanternPowered <https://github.com/LanternPowered>
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the Software), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED AS IS, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.lanternpowered.server.plugin;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import org.lanternpowered.server.game.Lantern;
import org.lanternpowered.server.game.LanternGame;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.plugin.PluginManager;
import org.spongepowered.plugin.meta.PluginMetadata;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LanternPluginManager implements PluginManager {

    private final Map<String, PluginContainer> plugins = new HashMap<>();
    private final Map<Object, PluginContainer> pluginInstances = new IdentityHashMap<>();

    private final LanternGame game;
    private final Path pluginsFolder;

    public LanternPluginManager(LanternGame game, Path pluginsFolder) {
        this.pluginsFolder = checkNotNull(pluginsFolder, "pluginsFolder");
        this.game = checkNotNull(game, "game");
    }

    public void registerPlugin(PluginContainer plugin) {
        checkNotNull(plugin, "plugin");
        this.plugins.put(plugin.getId(), plugin);
    }

    public void registerPluginInstances() {
        for (Map.Entry<String, PluginContainer> entry : this.plugins.entrySet()) {
            entry.getValue().getInstance().ifPresent(obj -> this.pluginInstances.put(obj, entry.getValue()));
        }
    }

    public void loadPlugins(boolean scanClasspath) throws IOException {
        this.game.getLogger().info("Searching for plugins...");

        PluginScanner pluginScanner = new PluginScanner();
        if (scanClasspath) {
            Lantern.getLogger().info("Scanning classpath for plugins...");

            final ClassLoader loader = LanternPluginManager.class.getClassLoader();
            if (loader instanceof URLClassLoader) {
                pluginScanner.scanClassPath((URLClassLoader) loader);
            } else {
                this.game.getLogger().error("Cannot search for plugins on classpath: Unsupported class loader: {}",
                        loader.getClass().getName());
            }
        }

        if (Files.exists(this.pluginsFolder)) {
            pluginScanner.scanDirectory(this.pluginsFolder);
        } else {
            // Create plugin folder
            Files.createDirectories(this.pluginsFolder);
        }

        Map<String, PluginCandidate> plugins = pluginScanner.getPlugins();
        this.game.getLogger().info("{} plugin(s) found", plugins.size());

        try {
            PluginHelper.sort(checkRequirements(plugins)).forEach(this::loadPlugin);
        } catch (Throwable e) {
            throw new RuntimeException("An error occurred while loading the plugins", e);
        }
    }

    private Set<PluginCandidate> checkRequirements(Map<String, PluginCandidate> candidates) {
        Set<PluginCandidate> successfulCandidates = new HashSet<>(candidates.size());
        List<PluginCandidate> failedCandidates = new ArrayList<>();

        for (PluginCandidate candidate : candidates.values()) {
            if (candidate.collectDependencies(this.plugins, candidates)) {
                successfulCandidates.add(candidate);
            } else {
                failedCandidates.add(candidate);
            }
        }

        if (failedCandidates.isEmpty()) {
            return successfulCandidates; // Nothing to do, all requirements satisfied
        }

        PluginCandidate candidate;
        boolean updated;
        while (true) {
            updated = false;
            Iterator<PluginCandidate> itr = successfulCandidates.iterator();
            while (itr.hasNext()) {
                candidate = itr.next();
                if (candidate.updateRequirements()) {
                    updated = true;
                    itr.remove();
                    failedCandidates.add(candidate);
                }
            }

            if (updated) {
                // Update failed candidates as well
                failedCandidates.forEach(PluginCandidate::updateRequirements);
            } else {
                break;
            }
        }

        for (PluginCandidate failed : failedCandidates) {
            if (failed.isInvalid()) {
                this.game.getLogger().error("Plugin '{}' from {} cannot be loaded because it is invalid",
                        failed.getId(), failed.getDisplaySource());
            } else {
                this.game.getLogger().error("Cannot load plugin '{}' from {} because it is missing the required dependencies {}",
                        failed.getId(), failed.getDisplaySource(), PluginHelper.formatRequirements(failed.getMissingRequirements()));
            }
        }

        return successfulCandidates;
    }

    private static final Method ADD_CLASS_LOADER_URL;

    static {
        try {
            ADD_CLASS_LOADER_URL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            ADD_CLASS_LOADER_URL.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addClassLoaderURL(URLClassLoader classLoader, URL url) {
        try {
            ADD_CLASS_LOADER_URL.invoke(classLoader, url);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadPlugin(PluginCandidate candidate) {
        final String id = candidate.getId();

        if (candidate.getSource().isPresent()) {
            try {
                addClassLoaderURL((URLClassLoader) LanternPluginManager.class.getClassLoader(),
                        candidate.getSource().get().toUri().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException("Failed to add plugin '" + id + "' from " + candidate.getDisplaySource() + " to classpath", e);
            }
        }

        final PluginMetadata metadata = candidate.getMetadata();
        final String name = firstNonNull(metadata.getName(), id);
        final String version = firstNonNull(metadata.getVersion(), "unknown");

        try {
            final Class<?> pluginClass = Class.forName(candidate.getPluginClass());
            final PluginContainer container = new LanternPluginContainer(id, pluginClass, metadata.getName(), metadata.getVersion(),
                    metadata.getDescription(), metadata.getUrl(), metadata.getAuthors(), candidate.getSource());
            this.registerPlugin(container);
            this.game.getEventManager().registerListeners(container, container.getInstance().get());

            this.game.getLogger().info("Loaded plugin: {} {} (from {})", name, version, candidate.getDisplaySource());
        } catch (Throwable e) {
            this.game.getLogger().error("Failed to load plugin: {} {} (from {})", name, version, candidate.getDisplaySource(), e);
        }
    }

    @Override
    public Optional<PluginContainer> fromInstance(Object instance) {
        checkNotNull(instance, "instance");
        if (instance instanceof PluginContainer) {
            return Optional.of((PluginContainer) instance);
        }
        return Optional.ofNullable(this.pluginInstances.get(instance));
    }

    @Override
    public Optional<PluginContainer> getPlugin(String id) {
        return Optional.ofNullable(this.plugins.get(checkNotNull(id, "identifier")));
    }

    @Override
    public Collection<PluginContainer> getPlugins() {
        return ImmutableList.copyOf(this.plugins.values());
    }

    @Override
    public boolean isLoaded(String id) {
        return this.plugins.containsKey(checkNotNull(id, "identifier"));
    }

}
