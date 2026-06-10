package dev.sakus.geyseredu.common.compat;

import java.util.OptionalInt;

public record ClientDescriptor(
    String playerName,
    boolean floodgatePlayer,
    OptionalInt bedrockProtocolVersion
) {
    public static ClientDescriptor unknownProtocol(String playerName, boolean floodgatePlayer) {
        return new ClientDescriptor(playerName, floodgatePlayer, OptionalInt.empty());
    }
}
