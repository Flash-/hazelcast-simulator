package com.hazelcast.simulator.protocol.handler;

import com.hazelcast.simulator.protocol.core.AddressLevel;
import com.hazelcast.simulator.protocol.core.Response;
import com.hazelcast.simulator.protocol.core.ResponseCodec;
import com.hazelcast.simulator.protocol.core.SimulatorAddress;
import com.hazelcast.simulator.protocol.core.SimulatorMessage;
import com.hazelcast.simulator.protocol.core.SimulatorMessageCodec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import org.apache.log4j.Logger;

import java.util.List;

import static com.hazelcast.simulator.protocol.core.ResponseCodec.isResponse;
import static com.hazelcast.simulator.protocol.core.SimulatorMessageCodec.isSimulatorMessage;
import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static java.lang.String.format;

/**
 * A {@link ByteToMessageDecoder} to decode a received {@link ByteBuf} to a {@link SimulatorMessage} or {@link Response}.
 *
 * If the destination address of a received {@link SimulatorMessage} is not for the {@link AddressLevel} of this Simulator
 * component, the {@link ByteBuf} is passed to the next handler.
 */
public class SimulatorProtocolDecoder extends ByteToMessageDecoder {

    private static final Logger LOGGER = Logger.getLogger(SimulatorProtocolDecoder.class);

    private final AttributeKey<Integer> forwardAddressIndex = AttributeKey.valueOf("forwardAddressIndex");

    private final SimulatorAddress localAddress;
    private final AddressLevel addressLevel;
    private final int addressLevelValue;

    public SimulatorProtocolDecoder(SimulatorAddress localAddress) {
        this.localAddress = localAddress;
        this.addressLevel = localAddress.getAddressLevel();
        this.addressLevelValue = addressLevel.toInt();
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        if (EMPTY_BUFFER.equals(buffer)) {
            return;
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(format("SimulatorProtocolDecoder.decode() %s %s", addressLevel, localAddress));
        }

        if (isSimulatorMessage(buffer)) {
            decodeSimulatorMessage(ctx, buffer, out);
            return;
        }
        if (isResponse(buffer)) {
            decodeResponse(ctx, buffer, out);
            return;
        }

        String msg = format("%s %s Invalid magic bytes %s", addressLevel, localAddress, buffer.toString(CharsetUtil.UTF_8));
        LOGGER.error(msg);
        throw new IllegalArgumentException(msg);
    }

    private void decodeSimulatorMessage(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
        long messageId = SimulatorMessageCodec.getMessageId(buffer);
        AddressLevel dstAddressLevel = AddressLevel.fromInt(SimulatorMessageCodec.getDestinationAddressLevel(buffer));
        LOGGER.debug(format("[%d] %s %s received a message for addressLevel %s", messageId, addressLevel, localAddress,
                dstAddressLevel));

        if (dstAddressLevel == addressLevel) {
            SimulatorMessage message = SimulatorMessageCodec.decodeSimulatorMessage(buffer);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(format("[%d] %s %s will consume %s", messageId, addressLevel, localAddress, message));
            }
            out.add(message);
        } else {
            int addressIndex = SimulatorMessageCodec.getChildAddressIndex(buffer, addressLevelValue);
            ctx.attr(forwardAddressIndex).set(addressIndex);

            out.add(buffer.duplicate());
            buffer.readerIndex(buffer.readableBytes());
            buffer.retain();
        }
    }

    private void decodeResponse(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
        long messageId = ResponseCodec.getMessageId(buffer);
        AddressLevel dstAddressLevel = AddressLevel.fromInt(ResponseCodec.getDestinationAddressLevel(buffer));
        LOGGER.debug(format("[%d] %s %s received a response for addressLevel %s", messageId, addressLevel, localAddress,
                dstAddressLevel));

        if (dstAddressLevel == addressLevel || dstAddressLevel.isParentAddressLevel(addressLevel)) {
            Response response = ResponseCodec.decodeResponse(buffer);
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace(format("[%d] %s %s received %s", response.getMessageId(), addressLevel, localAddress, response));
            }
            out.add(response);
        } else {
            int addressIndex = ResponseCodec.getChildAddressIndex(buffer, addressLevelValue);
            ctx.attr(forwardAddressIndex).set(addressIndex);

            out.add(buffer.duplicate());
            buffer.readerIndex(buffer.readableBytes());
            buffer.retain();
        }
    }
}