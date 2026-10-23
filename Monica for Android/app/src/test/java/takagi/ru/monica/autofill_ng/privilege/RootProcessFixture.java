package takagi.ru.monica.autofill_ng.privilege;

/** Child process fixture; no Android or real root needed. */
public class RootProcessFixture {
    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "denied": System.out.print("denied"); System.exit(1); break;
            case "wait": Thread.sleep(60000); break;
            case "large": for (int i = 0; i < 200000; i++) System.out.print('x'); break;
            default: System.out.print("0\n");
        }
    }
}
