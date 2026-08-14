import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Simpler & more correct: map (owner, humanName) -> srgName, skip desc matching.
 * SRG line: FD: owner/f_srg  owner/human  # desc desc
 */
public class RewriteModFields {
    static final Map<String, String> HUMAN_TO_SRG = new HashMap<>(); // "owner/human" -> srg

    public static void main(String[] args) throws Exception {
        parseSrg(args[0]);
        System.out.println("Field mappings loaded: " + HUMAN_TO_SRG.size());

        List<File> classes = new ArrayList<>();
        collectClasses(new File(args[1]), classes);
        int total = 0;

        for (File cf : classes) {
            byte[] bytes = Files.readAllBytes(cf.toPath());
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode(Opcodes.ASM9);
            cr.accept(cn, 0);
            int before = total;
            for (MethodNode mn : cn.methods) {
                for (AbstractInsnNode ins : mn.instructions.toArray()) {
                    if (ins instanceof FieldInsnNode fi) {
                        // Skip non-minecraft owners
                        if (!fi.owner.startsWith("net/minecraft")) continue;
                        String key = fi.owner + "/" + fi.name;
                        String srg = HUMAN_TO_SRG.get(key);
                        if (srg != null && !srg.equals(fi.name)) {
                            fi.name = srg;
                            total++;
                        }
                    }
                }
            }
            if (total > before) {
                ClassWriter cw;
                try {
                    cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    cn.accept(cw);
                    Files.write(cf.toPath(), cw.toByteArray());
                } catch (Exception e) {
                    cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    cn.accept(cw);
                    Files.write(cf.toPath(), cw.toByteArray());
                }
            }
        }
        System.out.println("Rewrote " + total + " field accesses");
    }

    static void parseSrg(String path) throws IOException {
        for (String line : Files.readAllLines(Path.of(path))) {
            line = line.trim();
            if (!line.startsWith("FD: ")) continue;
            String body = line.substring(4).split("#",2)[0].trim();
            String[] parts = body.split("\\s+");
            if (parts.length < 2) continue;
            String l = parts[0], r = parts[1];
            int s1 = l.lastIndexOf('/'), s2 = r.lastIndexOf('/');
            if (s1<0||s2<0) continue;
            String owner = l.substring(0, s1);
            String srg = l.substring(s1+1);
            String human = r.substring(s2+1);
            HUMAN_TO_SRG.put(owner + "/" + human, srg);
        }
    }

    static void collectClasses(File root, List<File> out) {
        File[] fs = root.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) collectClasses(f, out);
            else if (f.getName().endsWith(".class")) out.add(f);
        }
    }
}
