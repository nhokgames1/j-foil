import javax.tools.ToolProvider;
import javax.tools.JavaCompiler;
import java.io.File;

public class CompileCheck {
    public static void main(String[] args) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            System.out.println("No compiler found");
            return;
        }
        int result = compiler.run(null, null, null, 
            "-cp", 
            "C:\\Code\\J-Foil\\target\\classes;" + System.getProperty("java.class.path"), 
            "C:\\Code\\J-Foil\\src\\main\\java\\com\\jfoil\\controller\\TinfoilController.java");
        System.out.println("Compile result: " + result);
    }
}
