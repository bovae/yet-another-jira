package com.bovae.yaj.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DomainExceptionTest {

    private static final String DETAIL_MESSAGE = "something went wrong";

    static Stream<Class<? extends RuntimeException>> exceptionTypes() {
        return Stream.of(
                NotFoundException.class,
                ConflictException.class,
                ValidationException.class,
                UnauthorizedException.class);
    }

    @ParameterizedTest(name = "{0} should be a RuntimeException")
    @MethodSource("exceptionTypes")
    void constructor_shouldBeRuntimeException_whenInstantiated(Class<? extends RuntimeException> type)
            throws Exception {
        RuntimeException ex = type.getDeclaredConstructor(String.class).newInstance(DETAIL_MESSAGE);

        assertInstanceOf(RuntimeException.class, ex);
    }

    @ParameterizedTest(name = "{0} should round-trip detail message")
    @MethodSource("exceptionTypes")
    void getMessage_shouldReturnDetailMessage_whenConstructedWithMessage(Class<? extends RuntimeException> type)
            throws Exception {
        RuntimeException ex = type.getDeclaredConstructor(String.class).newInstance(DETAIL_MESSAGE);

        assertEquals(DETAIL_MESSAGE, ex.getMessage());
    }
}
