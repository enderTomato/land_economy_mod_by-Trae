import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/**
 * BuildBridges v2: correct handling for
 *   - static methods (copy ACC_STATIC from original)
 *   - virtual methods: rename-and-forward for override-friendly behavior
 *   - abstract methods: flip to make human-name the abstract contract
 */
public class BuildBridges {
    // class -> list of [humanName, desc, srgName]
    static final Map<String, List<String[]>> methodBridges = new HashMap<>();
    static final Map<String, List<String[]>> fieldBridges  = new HashMap<>();

    public static void main(String[] args) throws Exception {
        String inJar = args[0];
        String srg   = args[1];
        String outJar= args[2];
        parseSrg(srg);
        int mc = 0, fc = 0;
        for (var v : methodBridges.values()) mc += v.size();
        for (var v : fieldBridges.values())  fc += v.size();
        System.out.println("Parsed method bridges: " + mc + " / classes: " + methodBridges.size());
        System.out.println("Parsed field  bridges: " + fc + " / classes: " + fieldBridges.size());

        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (JarFile jf = new JarFile(inJar)) {
            Enumeration<JarEntry> en = jf.entries();
            while (en.hasMoreElements()) {
                JarEntry je = en.nextElement();
                if (je.isDirectory()) continue;
                byte[] bytes = jf.getInputStream(je).readAllBytes();
                String name = je.getName();
                if (name.endsWith(".class")) {
                    bytes = weave(name.substring(0, name.length() - 6), bytes);
                }
                entries.put(name, bytes);
            }
        }
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(outJar))) {
            for (var e : entries.entrySet()) {
                jos.putNextEntry(new JarEntry(e.getKey()));
                jos.write(e.getValue());
                jos.closeEntry();
            }
        }
        System.out.println("Wrote " + outJar);
    }

    static void parseSrg(String path) throws IOException {
        for (String line : Files.readAllLines(Path.of(path))) {
            line = line.trim();
            if (line.startsWith("MD: ")) {
                String[] parts = line.substring(4).split("\\s+");
                if (parts.length < 4) continue;
                String l = parts[0], ld = parts[1], r = parts[2];
                int s1 = l.lastIndexOf('/'), s2 = r.lastIndexOf('/');
                if (s1 < 0 || s2 < 0) continue;
                String cls = l.substring(0, s1);
                String srgName   = l.substring(s1 + 1);
                String humanName = r.substring(s2 + 1);
                if (srgName.startsWith("m_") && !srgName.equals(humanName) && !humanName.startsWith("m_")) {
                    methodBridges.computeIfAbsent(cls, k -> new ArrayList<>())
                                 .add(new String[]{humanName, ld, srgName});
                }
            } else if (line.startsWith("FD: ")) {
                String[] parts = line.substring(4).split("\\s+");
                if (parts.length < 2) continue;
                String l = parts[0], r = parts[1];
                int s1 = l.lastIndexOf('/'), s2 = r.lastIndexOf('/');
                if (s1 < 0 || s2 < 0) continue;
                String cls = l.substring(0, s1);
                String srgF   = l.substring(s1 + 1);
                String humanF = r.substring(s2 + 1);
                if (srgF.startsWith("f_") && !srgF.equals(humanF)) {
                    fieldBridges.computeIfAbsent(cls, k -> new ArrayList<>())
                                .add(new String[]{humanF, srgF});
                }
            }
        }
    }

    static byte[] weave(String className, byte[] bytes) {
        List<String[]> mbr = methodBridges.get(className);
        List<String[]> fbr = fieldBridges.get(className);
        if ((mbr == null || mbr.isEmpty()) && (fbr == null || fbr.isEmpty())) return bytes;

        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode(Opcodes.ASM9);
        cr.accept(cn, ClassReader.EXPAND_FRAMES);

        // Build original methods index
        Map<String, MethodNode> byNameDesc = new LinkedHashMap<>();
        for (MethodNode mn : cn.methods) byNameDesc.put(mn.name + mn.desc, mn);

        Map<String, String> fieldDescs = new HashMap<>();
        for (FieldNode fn : cn.fields) fieldDescs.put(fn.name, fn.desc);

        // 1. METHOD BRIDGES
        if (mbr != null) {
            for (String[] b : mbr) {
                String human = b[0], desc = b[1], srg = b[2];
                String srgKey = srg + desc;
                String humKey = human + desc;
                MethodNode orig = byNameDesc.get(srgKey);
                if (orig == null) continue;      // no srg method (shouldn't happen)
                if (byNameDesc.containsKey(humKey)) continue; // human already present

                boolean isStatic = (orig.access & Opcodes.ACC_STATIC) != 0;
                boolean isAbstract = (orig.access & Opcodes.ACC_ABSTRACT) != 0;
                boolean isInterface = (cn.access & Opcodes.ACC_INTERFACE) != 0;

                Type ret = Type.getReturnType(desc);
                Type[] args = Type.getArgumentTypes(desc);

                if (isStatic) {
                    // Copy original, also add a human-name static bridge that forwards
                    MethodNode bridge = new MethodNode(Opcodes.ASM9,
                            orig.access & ~Opcodes.ACC_ABSTRACT, human, desc, orig.signature,
                            (orig.exceptions == null) ? null : orig.exceptions.toArray(new String[0]));
                    InsnList il = bridge.instructions;
                    int slot = 0;
                    for (Type at : args) {
                        il.add(new VarInsnNode(at.getOpcode(Opcodes.ILOAD), slot));
                        slot += at.getSize();
                    }
                    il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, className, srg, desc, isInterface));
                    il.add(new InsnNode(ret.getOpcode(Opcodes.IRETURN)));
                    bridge.maxStack = 0; bridge.maxLocals = 0;
                    cn.methods.add(bridge);
                } else if (isAbstract) {
                    // 1. make srg concrete (body = this.human(args) + return)
                    orig.access &= ~Opcodes.ACC_ABSTRACT;
                    InsnList il = orig.instructions = new InsnList();
                    int slot = 1;
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    for (Type at : args) {
                        il.add(new VarInsnNode(at.getOpcode(Opcodes.ILOAD), slot));
                        slot += at.getSize();
                    }
                    il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, className, human, desc, isInterface));
                    il.add(new InsnNode(ret.getOpcode(Opcodes.IRETURN)));
                    // 2. add human method as abstract (same access as orig, but ABSTRACT)
                    int hAcc = (orig.access & ~Opcodes.ACC_NATIVE) | Opcodes.ACC_ABSTRACT;
                    MethodNode hum = new MethodNode(Opcodes.ASM9, hAcc, human, desc, orig.signature,
                            (orig.exceptions == null) ? null : orig.exceptions.toArray(new String[0]));
                    cn.methods.add(hum);
                } else {
                    // Concrete instance method: rename srg -> human, srg becomes forwarder to human
                    // (so overriding human in subclasses automatically affects srg callers too)
                    orig.name = human;
                    byNameDesc.remove(srgKey);
                    byNameDesc.put(humKey, orig);
                    // Now add a NEW forwarder srg method: srg(...) { return this.human(...); }
                    int access = orig.access & ~Opcodes.ACC_ABSTRACT;
                    MethodNode fwd = new MethodNode(Opcodes.ASM9, access, srg, desc, orig.signature,
                            (orig.exceptions == null) ? null : orig.exceptions.toArray(new String[0]));
                    InsnList il = fwd.instructions;
                    int slot = 1;
                    il.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    for (Type at : args) {
                        il.add(new VarInsnNode(at.getOpcode(Opcodes.ILOAD), slot));
                        slot += at.getSize();
                    }
                    il.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, className, human, desc, isInterface));
                    il.add(new InsnNode(ret.getOpcode(Opcodes.IRETURN)));
                    fwd.maxStack = 0; fwd.maxLocals = 0;
                    cn.methods.add(fwd);
                }
            }
        }

        // 2. FIELD BRIDGES: add alias fields with human names (duplicate data, but only way
        //    since Java has no field-name aliases; since this is compile-time only jar it's OK.
        //    At runtime real jar has correct srg field names, and field accesses in mod source
        //    will be rewritten by FML's coremods / access transformers.
        //    Actually — mod bytecode references field NAME+DESCRIPTOR. At runtime, the class has
        //    f_XXXXXX_ but not human alias → VerifyError!
        //    So for fields we must also fix mod bytecode. We'll also add a post-processing step:
        //    a small rewriter that changes field references in compiled mod classes from human
        //    back to srg names (we'll use srgToOfficial reversed map + field owner hint).
        //    For this pass, we still need javac to see HUMAN fields. Simplest: add public human
        //    fields with identical type alongside srg ones. At runtime, these extra fields are
        //    never touched because we rewrite the mod bytecode back to srg field names.
        if (fbr != null) {
            Set<String> fnames = new HashSet<>();
            for (FieldNode fn : cn.fields) fnames.add(fn.name);
            for (String[] b : fbr) {
                String human = b[0], srg = b[1];
                if (fnames.contains(human)) continue;
                String fdesc = fieldDescs.get(srg);
                if (fdesc == null) continue;
                FieldNode srgField = null;
                for (FieldNode fn : cn.fields) if (fn.name.equals(srg)) { srgField = fn; break; }
                if (srgField == null) continue;
                int acc = srgField.access | Opcodes.ACC_PUBLIC;
                // make it not static if original was static but also set value as default null
                FieldNode alias = new FieldNode(Opcodes.ASM9, acc, human, fdesc, srgField.signature, null);
                // preserve original static flag for the alias
                alias.access = srgField.access | Opcodes.ACC_PUBLIC;
                cn.fields.add(alias);
                fnames.add(human);
            }
        }

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        try {
            cn.accept(cw);
        } catch (Exception e) {
            // COMPUTE_FRAMES sometimes fails on obfuscated code; retry with COMPUTE_MAXS only
            System.err.println("ClassWriter frames failed on " + className + ": " + e.getMessage() + ", retrying with COMPUTE_MAXS only");
            ClassWriter cw2 = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw2);
            return cw2.toByteArray();
        }
        return cw.toByteArray();
    }
}
