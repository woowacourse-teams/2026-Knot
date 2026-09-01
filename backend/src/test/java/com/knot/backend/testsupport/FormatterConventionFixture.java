package com.knot.backend.testsupport;

import java.util.function.BiFunction;

final class FormatterConventionFixture extends FormatterConventionParent {

    FormatterConventionFixture(String name) {
        this(
                name,
                1
        );
    }

    FormatterConventionFixture(
            String name,
            int count
    ) {
        super(
                name,
                count
        );
    }

    static FormatterConventionRecord create(String name) {
        return new FormatterConventionRecord(
                name,
                1
        );
    }

    static FormatterSingleRecord createSingle(String name) {
        return new FormatterSingleRecord(name);
    }

    static void invokeZero() {
        consumeZero();
    }

    static FormatterConventionRecord create(
            String name,
            int count
    ) {
        return new FormatterConventionRecord(
                name,
                count
        );
    }

    static void invokeNested() {
        BiFunction<String, String, String> merger = (
                first,
                second
        ) -> first + second;

        consume(
                create(
                        "workspace",
                        1
                ),
                merger
        );
    }

    private static void consume(
            FormatterConventionRecord record,
            BiFunction<String, String, String> merger
    ) {}

    private static void consumeZero() {}
}

class FormatterConventionParent {

    FormatterConventionParent(
            String name,
            int count
    ) {}
}

record FormatterConventionRecord(
        String name,
        int count
) {
}

record FormatterSingleRecord(String name) {
}

enum FormatterConventionEnum {
    SINGLE("single"),
    MULTIPLE(
            "multiple",
            2
    );

    FormatterConventionEnum(String name) {}

    FormatterConventionEnum(
            String name,
            int count
    ) {}
}
