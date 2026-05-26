package hawk.JRegistryCenter.SSH;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.apache.sshd.server.SshServer;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import hawk.JRegistryCenter.Raft.RaftNode;
import hawk.JRegitstryCore.BPlusNode;
import java.util.concurrent.atomic.AtomicReference;
import hawk.JRegitstryCore.StateMachine;

@Slf4j
@Configuration
@Data
public class SSHServerConfig {
    @Value("${ssh.enabled:true}")
    private boolean enabled;
    @Value("${ssh.host}")
    private String host;
    @Value("${ssh.port}")
    private int port;
    @Value("${ssh.host-key-path}")
    private String hostKeyPath;
    @Value("${ssh.auth.username}")
    private String username;
    @Value("${ssh.auth.password}")
    private String password;
    private SshServer sshd;

    @Autowired
    private SSHService SSHService;

    @Autowired
    private RaftNode raftNode;

    @Autowired
    private StateMachine stateMachine;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("SSH server disabled by config.");
            return;
        }
        sshd = SshServer.setUpDefaultServer();
        sshd.setHost(host);
        sshd.setPort(port);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get(hostKeyPath)));
        sshd.setPasswordAuthenticator((u, p, s) -> username.equals(u) && password.equals(p));
        sshd.setShellFactory(channel -> new Command() {
            private InputStream in;
            private OutputStream out;
            private ExitCallback exitCallback;
            private volatile boolean running;
            private Thread worker;

            @Override
            public void setInputStream(InputStream in) {
                this.in = in;
            }

            @Override
            public void setOutputStream(OutputStream out) {
                this.out = out;
            }

            @Override
            public void setErrorStream(OutputStream err) {
                // Kept for interface compatibility, errors are written to out for simplicity.
            }

            @Override
            public void setExitCallback(ExitCallback callback) {
                this.exitCallback = callback;
            }

            @Override
            public void start(ChannelSession channelSession, Environment env) {
                running = true;
                AtomicReference<BPlusNode> sessionRoot = new AtomicReference<>(stateMachine.getRoot());
                worker = new Thread(() -> {
                    try (Terminal terminal = TerminalBuilder.builder()
                            .system(false)
                            .streams(in, out)
                            .build()) {
                        LineReader lineReader = LineReaderBuilder.builder()
                                .terminal(terminal)
                                .appName("JRegistryCenterCLI")
                                .build();
                        terminal.writer().println("JRegistryCenterCLI SSH connected. Type 'exit' to quit.");
                        terminal.flush();
                        while (running) {
                            String cmd;
                            try {
                                // JLine provides echo, backspace, arrow keys and history.
                                cmd = lineReader.readLine("> " + sessionRoot.get().pwd() + " ");
                            } catch (UserInterruptException e) {
                                // Ctrl+C: keep session alive and show a new prompt.
                                terminal.writer().println();
                                terminal.flush();
                                continue;
                            } catch (EndOfFileException e) {
                                // Ctrl+D: close the session.
                                break;
                            }

                            if (cmd == null) {
                                break;
                            }
                            cmd = cmd.trim();
                            if ("exit".equalsIgnoreCase(cmd) || "bye".equalsIgnoreCase(cmd)) {
                                terminal.writer().println("Bye.");
                                terminal.flush();
                                break;
                            }

                            String result = SSHService.userInputCheck(cmd, sessionRoot);
                            if (result != null && !result.isEmpty()) {
                                terminal.writer().println(result);
                                terminal.flush();
                            }
                        }
                        exitCallback.onExit(0);
                    } catch (IOException e) {
                        log.error("SSH shell error", e);
                        exitCallback.onExit(1, e.getMessage());
                    }
                }, "jregistry-ssh-shell");
                worker.setDaemon(true);
                worker.start();
            }

            @Override
            public void destroy(ChannelSession channelSession) {
                running = false;
                if (worker != null) {
                    worker.interrupt();
                }
            }
        });
        try {
            sshd.start();
            log.info("SSH server started on {}:{}", host, port);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start SSH server", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (sshd != null && !sshd.isClosed()) {
            try {
                sshd.stop();
                log.info("SSH server stopped.");
            } catch (IOException e) {
                log.warn("Failed to stop SSH server cleanly", e);
            }
        }
    }
}