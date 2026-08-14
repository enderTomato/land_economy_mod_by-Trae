import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/**
 * Post-weave step: apply Forge-specific patches to bridges.jar
 * - Add Explosion.getPosition() -> Vec3 (reads x/y/z fields)
 * - Add BlockEntity.getCapability(Capability) -> LazyOptional (stub returning empty)
 */
public class ForgeExtensions {
    static final String EXPLOSION = "net/minecraft/world/level/Explosion";
    static final String BLOCK_ENTITY = "net/minecraft/world/level/block/entity/BlockEntity";

    public static void main(String[] args) throws Exception {
        String bridgeJar = args[0];
        String outJar = args[1];

        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (JarFile jf = new JarFile(bridgeJar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                if (je.isDirectory()) continue;
                byte[] b = jf.getInputStream(je).readAllBytes();
                String name = je.getName();
                if (name.endsWith(".class")) {
                    String cn = name.substring(0, name.length() - 6);
                    if (EXPLOSION.equals(cn)) b = patchExplosion(cn, b);
                    else if (BLOCK_ENTITY.equals(cn)) b = patchBlockEntity(cn, b);
                }
                entries.put(name, b);
            }
        }
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outJar))) {
            for (var e : entries.entrySet()) {
                jos.putNextEntry(new JarEntry(e.getKey()));
                jos.write(e.getValue());
                jos.closeEntry();
            }
        }
        System.out.println("Forge extensions applied -> " + outJar);
    }

    // Find field with matching desc (e.g. D for double x, etc.) in class
    static String findFieldByNameHintOrType(ClassNode cn, String desc, Set<String> skipNames) {
        // Prefer field names that START WITH x/y/z lowercase for Vec3
        for (FieldNode fn : cn.fields) {
            if (!fn.desc.equals(desc)) continue;
            if (skipNames.contains(fn.name)) continue;
            if (fn.name.equals("x") || fn.name.equals("y") || fn.name.equals("z")
                    || fn.name.startsWith("x") || fn.name.startsWith("y") || fn.name.startsWith("z")) {
                return fn.name;
            }
        }
        // Fallback: first unused field of correct type
        for (FieldNode fn : cn.fields) {
            if (!fn.desc.equals(desc)) continue;
            if (skipNames.contains(fn.name)) continue;
            return fn.name;
        }
        return null;
    }

    static byte[] patchExplosion(String name, byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode(Opcodes.ASM9);
        cr.accept(cn, ClassReader.EXPAND_FRAMES);

        // Check if method exists
        String POS_DESC = "()Lnet/minecraft/world/phys/Vec3;";
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("getPosition") && mn.desc.equals(POS_DESC)) return bytes;
        }

        // Find x/y/z double fields
        Set<String> used = new HashSet<>();
        String xField = findFieldByNameHintOrType(cn, "D", used); if (xField != null) used.add(xField);
        String yField = findFieldByNameHintOrType(cn, "D", used); if (yField != null) used.add(yField);
        String zField = findFieldByNameHintOrType(cn, "D", used); if (zField != null) used.add(zField);
        // Actually there could be MANY doubles. Better approach: use positional order in exploded constructor
        // Or use x/y = center fields via SRG names search in print first.

        // Actually — Explosion class fields in vanilla MC: f_46015_ = x (D), f_46016_ = y (D), f_46017_ = z (D)
        // The field bridge pass already produced aliases for these if they had human names.
        // But not all fields map cleanly. Look for x/y/z aliases FIRST (human aliases) if we added them.
        xField = resolveOrFallback(cn, "x", "D", "f_46015_");
        yField = resolveOrFallback(cn, "y", "D", "f_46016_");
        zField = resolveOrFallback(cn, "z", "D", "f_46017_");

        // If the above vanilla names don't match, try f_46000_ range.
        // Fallback — any 3 double fields. Last resort: throw stub UnsupportedOperationException.
        boolean canUseFields = hasField(cn, xField, "D") && hasField(cn, yField, "D") && hasField(cn, zField, "D");

        MethodNode mn = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC, "getPosition", POS_DESC, null, null);
        InsnList il = mn.instructions;
        if (canUseFields) {
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, name, xField, "D"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, name, yField, "D"));
            il.add(new VarInsnNode(Opcodes.ALOAD, 0));
            il.add(new FieldInsnNode(Opcodes.GETFIELD, name, zField, "D"));
            il.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/minecraft/world/phys/Vec3", "<init>", "(DDD)V", false));
        } else {
            // Stub: return Vec3.ZERO (safer than crash)
            il.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/phys/Vec3", "ZERO", "Lnet/minecraft/world/phys/Vec3;"));
        }
        il.add(new InsnNode(Opcodes.ARETURN));
        mn.maxStack = 6; mn.maxLocals = 1;
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        try { cn.accept(cw); return cw.toByteArray(); }
        catch (Exception e) {
            ClassWriter cw2 = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw2); return cw2.toByteArray();
        }
    }

    static String resolveOrFallback(ClassNode cn, String humanName, String desc, String srgGuess) {
        boolean hasHuman = false, hasSrg = false;
        for (FieldNode fn : cn.fields) {
            if (fn.name.equals(humanName) && fn.desc.equals(desc)) hasHuman = true;
            if (fn.name.equals(srgGuess) && fn.desc.equals(desc)) hasSrg = true;
        }
        if (hasHuman) return humanName;
        if (hasSrg) return srgGuess;
        return humanName; // return anyway if missing, caller checks validity
    }
    static boolean hasField(ClassNode cn, String name, String desc) {
        for (FieldNode fn : cn.fields)
            if (fn.name.equals(name) && fn.desc.equals(desc)) return true;
        return false;
    }

    static byte[] patchBlockEntity(String name, byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode(Opcodes.ASM9);
        cr.accept(cn, ClassReader.EXPAND_FRAMES);

        // Add getCapability(Capability<T>)LazyOptional<T> — stub returns empty
        String DESC_GETCAP = "(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;";
        for (MethodNode mn : cn.methods)
            if (mn.name.equals("getCapability") && mn.desc.equals(DESC_GETCAP)) return bytes;

        MethodNode mn = new MethodNode(Opcodes.ASM9,
                Opcodes.ACC_PUBLIC, "getCapability", DESC_GETCAP, "<T:Ljava/lang/Object;>(Lnet/minecraftforge/common/capabilities/Capability<TT;>;)Lnet/minecraftforge/common/util/LazyOptional<TT;>;", null);
        InsnList il = mn.instructions;
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/minecraftforge/common/util/LazyOptional", "empty", "()Lnet/minecraftforge/common/util/LazyOptional;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        mn.maxStack = 1; mn.maxLocals = 2;
        cn.methods.add(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }
}
