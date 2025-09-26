package me.iris.ambien.obfuscator.wrappers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;
import me.iris.ambien.obfuscator.Ambien;
import me.iris.ambien.obfuscator.asm.CompetentClassWriter;
import me.iris.ambien.obfuscator.builders.MethodBuilder;
import me.iris.ambien.obfuscator.wrappers.MethodWrapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.RETURN;

public class ClassWrapper {
    private final String name;
    @Getter
    @Setter
    private ClassNode node;
    private final List<MethodWrapper> methods;
    private final boolean isLibraryClass;

    public ClassWrapper(String name, ClassNode node, boolean isLibraryClass) {
        this.name = name;
        this.node = node;
        this.methods = new ArrayList<MethodWrapper>();
        this.isLibraryClass = isLibraryClass;
        Arrays.stream(node.methods.toArray()).map(methodObj -> (MethodNode)methodObj).forEach(methodNode -> this.methods.add(new MethodWrapper((MethodNode)methodNode)));
    }

    public List<MethodWrapper> getTransformableMethods() {
        return this.methods.stream().filter(method -> !Ambien.get.exclusionManager.isMethodExcluded(this.name, method.getNode().name)).collect(Collectors.toList());
    }

    public MethodNode getStaticInitializer() {
        // Check if the init method already exists in the class.
        for (MethodWrapper wrapper : methods) {
            if (wrapper.getNode().name.equals("<clinit>"))
                return wrapper.getNode();
        }

        // Create init method & return it
        final MethodBuilder builder = new MethodBuilder()
                .setAccess(ACC_STATIC)
                .setName("<clinit>")
                .setDesc("()V");
        final MethodNode methodNode = builder.buildNode();

        // Add return insn
        methodNode.instructions.add(new InsnNode(RETURN));

        // Add method to class
        addMethod(methodNode);

        // Return method
        return methodNode;
    }

    public boolean isInterface() {
        return (this.node.access & 0x200) == 512;
    }

    public boolean isEnum() {
        return (this.node.access & 0x4000) == 16384;
    }

    public CopyOnWriteArrayList<FieldNode> getFields() {
        CopyOnWriteArrayList<FieldNode> fields = new CopyOnWriteArrayList<FieldNode>();
        for (FieldNode fieldObj : this.node.fields) {
            fields.add(fieldObj);
        }
        return fields;
    }

    public void addField(FieldNode fieldNode) {
        this.node.fields.add(fieldNode);
    }

    public void addMethod(MethodNode methodNode) {
        this.node.methods.add(methodNode);
        this.methods.add(new MethodWrapper(methodNode));
    }

    public byte[] toByteArray() {
        Ambien.LOGGER.debug("Converting class to bytes: {}", (Object)this.name);
        try {
            CompetentClassWriter writer = new CompetentClassWriter(2);
            this.node.accept(writer);
            return writer.toByteArray();
        }
        catch (ArrayIndexOutOfBoundsException | NegativeArraySizeException | TypeNotPresentException e) {
            if (e instanceof NegativeArraySizeException) {
                Ambien.LOGGER.warn("NegativeArraySizeException thrown when attempting to write class \"{}\" using COMPUTE_MAXS, this is most likely caused by malformed bytecode.", (Object)this.name);
            } else {
                Ambien.LOGGER.warn("Attempting to write class \"{}\" using COMPUTE_MAXS, some errors may appear during runtime.", (Object)this.name);
            }
            CompetentClassWriter writer = new CompetentClassWriter(1);
            this.node.accept(writer);
            return writer.toByteArray();
        }
    }

    public String getName() {
        return this.name;
    }

    public List<MethodWrapper> getMethods() {
        return this.methods;
    }

    public boolean isLibraryClass() {
        return this.isLibraryClass;
    }
}
 