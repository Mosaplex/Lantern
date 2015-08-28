package org.lanternpowered.server.network.protocol;

import org.lanternpowered.server.network.message.MessageRegistry;
import org.lanternpowered.server.network.vanilla.message.codec.status.CodecStatusInOutPing;
import org.lanternpowered.server.network.vanilla.message.codec.status.CodecStatusInRequest;
import org.lanternpowered.server.network.vanilla.message.codec.status.CodecStatusOutResponse;
import org.lanternpowered.server.network.vanilla.message.type.status.MessageStatusInOutPing;
import org.lanternpowered.server.network.vanilla.message.type.status.MessageStatusInRequest;
import org.lanternpowered.server.network.vanilla.message.type.status.MessageStatusOutResponse;

public final class ProtocolStatus extends ProtocolBase {

    public ProtocolStatus() {
        MessageRegistry inbound = this.inbound();
        MessageRegistry outbound = this.outbound();

        // TODO: Add handlers
        inbound.register(0x00, MessageStatusInRequest.class, CodecStatusInRequest.class);
        inbound.register(0x01, MessageStatusInOutPing.class, CodecStatusInOutPing.class);

        outbound.register(0x00, MessageStatusOutResponse.class, CodecStatusOutResponse.class);
        outbound.register(0x01, MessageStatusInOutPing.class, CodecStatusInOutPing.class);
    }
}
