import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.AbstractASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformationClass

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

System.setProperty("clover.logging.level", "REPLACE_ME_LOGGING_LEVEL")

// Workaround for https://github.com/gradle/gradle/issues/5908
// Inspired by https://discuss.gradle.org/t/working-around-a-loader-constraint-violation/3138
withConfig(configuration) {
    ast(Spoofed)
}

@Retention(RetentionPolicy.RUNTIME)
@GroovyASTTransformationClass("ClassLoaderSpoofer")
@interface Spoofed {}

@GroovyASTTransformation(phase = CompilePhase.CONVERSION)
class ClassLoaderSpoofer extends AbstractASTTransformation implements CompilationUnitAware {
    @Override
    void visit(final ASTNode[] nodes, final SourceUnit source) {
    }

    @Override
    void setCompilationUnit(final CompilationUnit unit) {
        unit.transformLoader.parent.classNames.add("groovyjarjarasm.asm.MethodVisitor")
        unit.transformLoader.parent.classNames.add("groovyjarjarasm.asm.Label")
    }
}