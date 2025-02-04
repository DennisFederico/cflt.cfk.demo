package io.cflt.dfederico.streams.errors;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.errors.*;
import org.apache.kafka.streams.processor.api.Record;

import java.util.Map;


@Slf4j
public class CatchAllErrorHandler implements ProductionExceptionHandler, ProcessingExceptionHandler, StreamsUncaughtExceptionHandler {

    @Override
    public void configure(Map<String, ?> configs) {
        log.info(">>>>>>>>>>>>>>> Configuring CatchAllErrorHandler");
    }

    // ProductionExceptionHandler
    @Override
    public ProductionExceptionHandlerResponse handle(ErrorHandlerContext context, ProducerRecord<byte[], byte[]> record, Exception exception) {
        log.error(">>>>>>>>>>>>>>> ProductionExceptionHandler - DELEGATE TO SUPER - handling record: {}", record, exception);
        return ProductionExceptionHandler.super.handle(context, record, exception);
    }

    // ProductionExceptionHandler
    @Override
    public ProductionExceptionHandlerResponse handleSerializationException(ErrorHandlerContext context, ProducerRecord record, Exception exception, SerializationExceptionOrigin origin) {
        log.error(">>>>>>>>>>>>>>> ProductionExceptionHandler - DELEGATE TO SUPER - handling record: {}", record, exception);
        return ProductionExceptionHandler.super.handleSerializationException(context, record, exception, origin);
    }

    // ProcessingExceptionHandler
    @Override
    public ProcessingHandlerResponse handle(ErrorHandlerContext context, Record<?, ?> record, Exception exception) {
        log.error(">>>>>>>>>>>>>>> ProcessingExceptionHandler - FAIL - handling record: {}", record, exception);
        return ProcessingHandlerResponse.FAIL;
    }

    // StreamsUncaughtExceptionHandler
    @Override
    public StreamThreadExceptionResponse handle(Throwable exception) {
        log.error(">>>>>>>>>>>>>>> StreamsUncaughtExceptionHandler - SHUTDOWN_CLIENT - handling exception:", exception);
        return StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
    }
}
