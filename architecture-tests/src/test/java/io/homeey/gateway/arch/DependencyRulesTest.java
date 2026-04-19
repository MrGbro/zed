package io.homeey.gateway.arch;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.homeey.gateway")
class DependencyRulesTest {

    @ArchTest
    static final ArchRule plugins_should_not_depend_on_core =
            noClasses().that().resideInAnyPackage("..plugin..")
                    .should().dependOnClassesThat().resideInAnyPackage("..core..");

    @ArchTest
    static final ArchRule api_should_not_depend_on_impl =
            noClasses().that().resideInAnyPackage("..api..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..nacos..",
                            "..netty..",
                            "..otel..",
                            "..bootstrap..",
                            "..admin.."
                    );

    @ArchTest
    static final ArchRule core_should_not_depend_on_transport_specific_types =
            noClasses().that().resideInAnyPackage("..core..")
                    .should().dependOnClassesThat().resideInAnyPackage("io.netty..", "reactor..");
}
