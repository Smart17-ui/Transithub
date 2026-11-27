package com.transithub.architecture;

import java.util.Optional;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the modular-monolith rules from ADR-0002 and AC-R1-X-04 / X-07.
 *
 * <p>A <em>module</em> is a direct sub-package of {@code com.transithub}
 * (for example {@code onboarding}). {@code shared} is the shared kernel that
 * every module may use. A module's <em>public API</em> is the types directly
 * in its root package; everything in its sub-packages is internal.
 */
@AnalyzeClasses(packages = "com.transithub", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    private static final String ROOT = "com.transithub.";
    private static final String SHARED = "shared";

    /** Modules may use another module only through its root (public) package. */
    @ArchTest
    static final ArchRule modulesUseOnlyEachOthersPublicApi =
            classes().that().resideInAPackage("com.transithub.(*)..")
                    .should(new ArchCondition<JavaClass>(
                            "only depend on other modules through their public root package") {
                        @Override
                        public void check(JavaClass origin, ConditionEvents events) {
                            Optional<String> originModule = moduleOf(origin.getPackageName());
                            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                                String targetPackage = dependency.getTargetClass().getPackageName();
                                Optional<String> targetModule = moduleOf(targetPackage);

                                boolean crossesModules = targetModule.isPresent()
                                        && !targetModule.get().equals(SHARED)
                                        && !targetModule.equals(originModule);
                                boolean reachesInternals = targetModule.isPresent()
                                        && !targetPackage.equals(ROOT + targetModule.get());

                                if (crossesModules && reachesInternals) {
                                    events.add(SimpleConditionEvent.violated(dependency,
                                            dependency.getDescription()
                                                    + " reaches into the internals of module '"
                                                    + targetModule.get() + "'"));
                                }
                            }
                        }
                    })
                    .allowEmptyShould(true);

    /** The shared kernel must not know about any business module. */
    @ArchTest
    static final ArchRule sharedKernelDependsOnNoModule =
            noClasses().that().resideInAPackage("com.transithub.shared..")
                    .should().dependOnClassesThat(new com.tngtech.archunit.base.DescribedPredicate<JavaClass>(
                            "belong to a business module") {
                        @Override
                        public boolean test(JavaClass target) {
                            return moduleOf(target.getPackageName())
                                    .filter(module -> !module.equals(SHARED))
                                    .isPresent();
                        }
                    });

    /**
     * Lifecycles change only through commands (ADR-0006): no API request
     * body (a {@code *Request} type in an {@code api} package) may carry a
     * status field that a client could set directly.
     */
    @ArchTest
    static final ArchRule requestsCannotSetStatus =
            fields().that().areDeclaredInClassesThat().resideInAPackage("..api..")
                    .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Request")
                    .should().notHaveName("status")
                    .allowEmptyShould(true);

    private static Optional<String> moduleOf(String packageName) {
        if (!packageName.startsWith(ROOT)) {
            return Optional.empty();
        }
        String rest = packageName.substring(ROOT.length());
        int dot = rest.indexOf('.');
        return Optional.of(dot < 0 ? rest : rest.substring(0, dot));
    }
}
