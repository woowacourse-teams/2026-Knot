package com.knot.backend.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.knot.backend.workspace.domain.ContentSourceProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderNeutralCoreArchitectureTest {
    private static final Path WORKSPACE_SOURCE_ROOT = Path.of("src/main/java/com/knot/backend/workspace");
    private static final String PROVIDER_DECLARATION_FILE = "ContentSourceProvider.java";
    private static final Set<String> PROVIDER_IDENTIFIERS = Arrays.stream(ContentSourceProvider.values())
            .map(ContentSourceProvider::name)
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    @DisplayName("Application 소스는 특정 콘텐츠 공급자 이름에 의존하지 않는다")
    @Test
    void applicationSources_success_providerNeutral() throws IOException {
        // given
        Path applicationSourceRoot = WORKSPACE_SOURCE_ROOT.resolve("application");

        // when
        List<String> providerReferences = findProviderReferences(
                applicationSourceRoot,
                false
        );

        // then
        assertThat(applicationSourceRoot).isDirectory();
        assertThat(providerReferences).isEmpty();
    }

    @DisplayName("Domain 소스는 콘텐츠 공급자 식별자 외에 특정 공급자 이름에 의존하지 않는다")
    @Test
    void domainSources_success_providerNeutralExceptProviderIdentifier() throws IOException {
        // given
        Path domainSourceRoot = WORKSPACE_SOURCE_ROOT.resolve("domain");

        // when
        List<String> providerReferences = findProviderReferences(
                domainSourceRoot,
                true
        );

        // then
        assertThat(domainSourceRoot).isDirectory();
        assertThat(providerReferences).isEmpty();
    }

    @DisplayName("공통 Persistence 소스는 특정 콘텐츠 공급자 이름에 의존하지 않는다")
    @Test
    void persistenceSources_success_providerNeutral() throws IOException {
        // given
        Path persistenceSourceRoot = WORKSPACE_SOURCE_ROOT.resolve("infrastructure/persistence");

        // when
        List<String> providerReferences = findProviderReferences(
                persistenceSourceRoot,
                false
        );

        // then
        assertThat(persistenceSourceRoot).isDirectory();
        assertThat(providerReferences).isEmpty();
    }

    private List<String> findProviderReferences(
            Path sourceRoot,
            boolean allowContentSourceProviderIdentifier
    ) throws IOException {
        List<String> references = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path path : paths.filter(this::isJavaSource)
                    .sorted()
                    .toList()) {
                List<String> lines = Files.readAllLines(path);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    if (isProviderDeclaration(
                            path,
                            allowContentSourceProviderIdentifier
                    )) {
                        continue;
                    }
                    String normalizedLine = line.toLowerCase(Locale.ROOT);
                    if (PROVIDER_IDENTIFIERS.stream()
                            .anyMatch(normalizedLine::contains)) {
                        references.add(
                                "%s:%d".formatted(
                                        sourceRoot.relativize(path),
                                        index + 1
                                )
                        );
                    }
                }
            }
        }
        return references;
    }

    private boolean isJavaSource(Path path) {
        return Files.isRegularFile(path) && path.toString()
                .endsWith(".java");
    }

    private boolean isProviderDeclaration(
            Path path,
            boolean allowContentSourceProviderIdentifier
    ) {
        return allowContentSourceProviderIdentifier && path.getFileName()
                .toString()
                .equals(PROVIDER_DECLARATION_FILE);
    }
}
