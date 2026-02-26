package hawk.JRegistryClient;

import java.util.Scanner;
public class JRegistryClient {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("JRegistryClient started. Type 'exit' to quit.");

        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(line)) {
                System.out.println("Bye.");
                break;
            }

            // TODO: 在这里解析用户命令，并调用你的 JRegistry 服务
            System.out.println("You typed: " + line);
        }

        scanner.close();
    }

}
