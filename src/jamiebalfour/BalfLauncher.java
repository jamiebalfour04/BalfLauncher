package jamiebalfour;

import java.awt.Desktop;
import java.awt.desktop.OpenURIEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.file.*;
import java.util.*;

/**
 * BalfLauncher is a fast and easy way to launch Jamie Balfour's
 * programs via protocol handlers.
 */
public class BalfLauncher {

  static final Path INSTALL_DIR = HelperFunctions
          .getAppDataDirectory("jamiebalfour/launcher",
                  System.getProperty("user.home") + "/jb/launcher")
          .toPath()
          .toAbsolutePath()
          .normalize();

  static final Path INSTALL_JAR = INSTALL_DIR.resolve("balflauncher.jar");
  private static final Path LOG_FILE = INSTALL_DIR.resolve("launcher.log");

  public static void main(String[] args) {

    try {

      log("--------------------------------------------------");
      log("BalfLauncher started.");
      log("Code source: " + BalfLauncher.class.getProtectionDomain().getCodeSource().getLocation());

      String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
      log("OS: " + os);

      if (os.contains("mac")) {
        installMacOpenUriHandler();
      }

      if (args != null && args.length > 0) {

        log("BalfLauncher arguments entered. Count: " + args.length);

        if ("--install".equalsIgnoreCase(args[0])) {
          log("Install argument detected.");
          installLauncher();
          return;
        }

        for (int i = 0; i < args.length; i++) {
          log("arg[" + i + "] = " + args[i]);
        }

        for (String arg : args) {
          if (arg != null && arg.startsWith("balf://")) {
            log("Protocol argument detected: " + arg);
            handleIncomingProtocol(arg);
            return;
          }
        }
      } else {
        log("BalfLauncher has no arguments.");
      }

      if (os.contains("mac")) {
        log("Waiting for OpenURI event...");
        Thread.sleep(15000);
        log("No OpenURI event received within wait window.");
        return;
      }

      launchNormally();

    } catch (Exception ex) {
      log("Exception in main: " + ex);
      ex.printStackTrace();
    }
  }

  private static void launchNormally() {
    log("launchNormally() called.");
    System.out.println("Balf Launcher started.");
  }

  private static void log(String message) {
    try {
      Files.createDirectories(INSTALL_DIR);
      Files.writeString(
              LOG_FILE,
              message + System.lineSeparator(),
              StandardOpenOption.CREATE,
              StandardOpenOption.APPEND
      );
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static void installMacOpenUriHandler() {
    try {
      if (!Desktop.isDesktopSupported()) {
        log("Desktop is not supported.");
        return;
      }

      Desktop desktop = Desktop.getDesktop();

      desktop.setOpenURIHandler((OpenURIEvent event) -> {
        try {
          URI uri = event.getURI();

          if (uri == null) {
            log("OpenURI event received with null URI.");
            return;
          }

          String rawUrl = uri.toString();
          log("OpenURI event received: " + rawUrl);

          handleIncomingProtocol(rawUrl);

        } catch (Exception ex) {
          log("Exception in OpenURI handler: " + ex);
          ex.printStackTrace();
        }
      });

      log("macOS OpenURI handler installed.");

    } catch (Exception ex) {
      log("Failed to install macOS OpenURI handler: " + ex);
      ex.printStackTrace();
    }
  }

  private static void handleIncomingProtocol(String rawUrl) {
    try {
      String target = handleProtocol(rawUrl);
      log("Resolved target: " + target);
      System.out.println("Resolved target: " + target);

      handleResolvedProtocol(rawUrl, target);

    } catch (Exception ex) {
      log("Exception in handleIncomingProtocol: " + ex);
      ex.printStackTrace();
    }
  }

  private static void handleResolvedProtocol(String rawUrl, String target) throws IOException {
    if (target == null || target.isEmpty()) {
      log("No target resolved for protocol: " + rawUrl);
      return;
    }

    if (rawUrl.toLowerCase(Locale.ROOT).contains("zpe")) {
      log("Launching ZPE with -g");
      new ProcessBuilder("zpe", "-g")
              .redirectErrorStream(true)
              .start();
      return;
    }

    log("No application mapping found for protocol: " + rawUrl);
  }

  private static void installLauncher() throws IOException, URISyntaxException {

    Path currentJar = Paths.get(
            BalfLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()
    ).toAbsolutePath().normalize();

    log("Installing Balf Launcher from: " + currentJar);
    log("To: " + INSTALL_DIR);

    System.out.println("Installing Balf Launcher from: " + currentJar);
    System.out.println("To: " + INSTALL_DIR);

    Files.createDirectories(INSTALL_DIR);
    Files.copy(currentJar, INSTALL_JAR, StandardCopyOption.REPLACE_EXISTING);

    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);

    if (os.contains("mac")) {
      installOnMac();
    } else if (os.contains("win")) {
      installOnWindows();
    } else {
      installOnLinux();
    }

    log("Installed Balf Launcher to: " + INSTALL_DIR);
    System.out.println("Installed Balf Launcher to: " + INSTALL_DIR);
  }

  // ---------------- macOS ----------------

  private static void installOnMac() throws IOException {

    Path appsDir = Paths.get(System.getProperty("user.home"), "Applications");
    Files.createDirectories(appsDir);

    buildMacAppBundle(INSTALL_JAR, appsDir);

    Path appBundle = appsDir.resolve("BalfLauncher.app");
    registerMacAppBundle(appBundle);

    log("Balf Launcher installed on macOS.");
    System.out.println("Balf Launcher installed on macOS.");
  }

  public static void buildMacAppBundle(Path jarPath, Path outputDir) throws IOException {

    Path app = outputDir.resolve("BalfLauncher.app");
    Path contents = app.resolve("Contents");
    Path macos = contents.resolve("MacOS");
    Path javaDir = contents.resolve("Java");

    Files.createDirectories(macos);
    Files.createDirectories(javaDir);

    Path jarTarget = javaDir.resolve("balflauncher.jar");
    Files.copy(jarPath, jarTarget, StandardCopyOption.REPLACE_EXISTING);

    Path launcher = macos.resolve("balflauncher");

    String launcherScript =
            "#!/bin/bash\n" +
                    "DIR=\"$(cd \"$(dirname \"$0\")/..\" && pwd)\"\n" +
                    "JAVA=$(/usr/libexec/java_home)/bin/java\n" +
                    "exec \"$JAVA\" -jar \"$DIR/Java/balflauncher.jar\" \"$@\"\n";

    Files.writeString(launcher, launcherScript);
    launcher.toFile().setExecutable(true);

    Path plist = contents.resolve("Info.plist");

    String plistContent =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                    "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" " +
                    "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                    "<plist version=\"1.0\">\n" +
                    "<dict>\n" +
                    "    <key>CFBundleName</key>\n" +
                    "    <string>BalfLauncher</string>\n" +
                    "    <key>CFBundleIdentifier</key>\n" +
                    "    <string>scot.jamiebalfour.balflauncher</string>\n" +
                    "    <key>CFBundleExecutable</key>\n" +
                    "    <string>balflauncher</string>\n" +
                    "    <key>CFBundlePackageType</key>\n" +
                    "    <string>APPL</string>\n" +
                    "    <key>CFBundleVersion</key>\n" +
                    "    <string>1.0</string>\n" +
                    "    <key>CFBundleShortVersionString</key>\n" +
                    "    <string>1.0</string>\n" +
                    "    <key>CFBundleURLTypes</key>\n" +
                    "    <array>\n" +
                    "        <dict>\n" +
                    "            <key>CFBundleURLName</key>\n" +
                    "            <string>scot.jamiebalfour.balflauncher</string>\n" +
                    "            <key>CFBundleURLSchemes</key>\n" +
                    "            <array>\n" +
                    "                <string>balf</string>\n" +
                    "            </array>\n" +
                    "        </dict>\n" +
                    "    </array>\n" +
                    "</dict>\n" +
                    "</plist>\n";

    Files.writeString(plist, plistContent);

    log("Built macOS app bundle at: " + app);
    System.out.println("Built macOS app bundle at: " + app);
  }

  private static void registerMacAppBundle(Path appBundle) throws IOException {

    Path lsregister = Paths.get(
            "/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister"
    );

    runCommand(lsregister.toString(), "-f", appBundle.toString());

    log("Registered macOS protocol handler.");
    System.out.println("Registered macOS protocol handler.");
  }

  // ---------------- Windows ----------------

  private static void installOnWindows() throws IOException {

    Path cmd = INSTALL_DIR.resolve("balflauncher.cmd");

    String content =
            "@echo off\r\n" +
                    "java -jar \"" + INSTALL_JAR + "\" %*\r\n";

    Files.writeString(cmd, content);

    registerWindowsProtocolHandler(cmd);

    log("Balf Launcher installed on Windows.");
    System.out.println("Balf Launcher installed on Windows.");
  }

  private static void registerWindowsProtocolHandler(Path launcherPath) throws IOException {

    String launcher = launcherPath.toAbsolutePath().normalize().toString();

    runRegAdd("HKCU\\Software\\Classes\\balf", "", "URL:BalfLauncher Protocol");
    runRegAdd("HKCU\\Software\\Classes\\balf", "URL Protocol", "");
    runRegAdd("HKCU\\Software\\Classes\\balf\\shell\\open\\command",
            "",
            "cmd.exe /c \"\"" + launcher + "\" \"%1\"\"");

    log("Registered balf:// protocol on Windows.");
    System.out.println("Registered balf:// protocol on Windows.");
  }

  // ---------------- Linux ----------------

  private static void installOnLinux() throws IOException {

    Path launcher = INSTALL_DIR.resolve("balflauncher");

    String content =
            "#!/bin/sh\n" +
                    "java -jar \"" + INSTALL_JAR + "\" \"$@\"\n";

    Files.writeString(launcher, content);
    launcher.toFile().setExecutable(true);

    registerLinuxProtocolHandler(launcher);

    log("Balf Launcher installed on Linux.");
    System.out.println("Balf Launcher installed on Linux.");
  }

  private static void registerLinuxProtocolHandler(Path launcherPath) throws IOException {

    Path appsDir = Paths.get(System.getProperty("user.home"), ".local", "share", "applications");
    Files.createDirectories(appsDir);

    Path desktopFile = appsDir.resolve("balf.desktop");

    String content =
            "[Desktop Entry]\n" +
                    "Name=BalfLauncher\n" +
                    "Exec=\"" + launcherPath.toAbsolutePath() + "\" %u\n" +
                    "Type=Application\n" +
                    "Terminal=false\n" +
                    "MimeType=x-scheme-handler/balf;\n";

    Files.writeString(desktopFile, content);

    runCommand("xdg-mime", "default", "balf.desktop", "x-scheme-handler/balf");

    log("Registered balf:// protocol on Linux.");
    System.out.println("Registered balf:// protocol on Linux.");
  }

  // ---------------- Registry helper ----------------

  private static void runRegAdd(String key, String valueName, String data) throws IOException {

    List<String> command = new ArrayList<>();
    command.add("reg");
    command.add("add");
    command.add(key);
    command.add("/f");

    if (valueName == null || valueName.isEmpty()) {
      command.add("/ve");
    } else {
      command.add("/v");
      command.add(valueName);
    }

    command.add("/t");
    command.add("REG_SZ");
    command.add("/d");
    command.add(data);

    runCommand(command.toArray(new String[0]));
  }

  private static void runCommand(String... cmd) throws IOException {

    try {

      Process process = new ProcessBuilder(cmd)
              .redirectErrorStream(true)
              .start();

      try (BufferedReader reader =
                   new BufferedReader(new InputStreamReader(process.getInputStream()))) {

        String line;
        while ((line = reader.readLine()) != null) {
          System.out.println(line);
          log("CMD> " + line);
        }
      }

      int exit = process.waitFor();

      if (exit != 0) {
        throw new IOException("Command failed: " + String.join(" ", cmd));
      }

    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IOException("Command interrupted.");
    }
  }

  // ---------------- Protocol parsing ----------------

  static String handleProtocol(String rawUrl) {

    try {

      URI uri = new URI(rawUrl);

      String action = uri.getHost();
      String query = uri.getRawQuery();

      Map<String, String> params = parseQuery(query);

      if ("open".equalsIgnoreCase(action)) {

        if (params.containsKey("url")) {
          return params.get("url");
        }

        if (params.containsKey("file")) {
          return params.get("file");
        }
      }

      log("Unknown protocol or action: " + rawUrl);

    } catch (Exception ex) {
      log("Exception in handleProtocol: " + ex);
      ex.printStackTrace();
    }

    return "";
  }

  private static Map<String, String> parseQuery(String query) throws Exception {

    Map<String, String> map = new HashMap<>();

    if (query == null || query.isEmpty()) {
      return map;
    }

    String[] pairs = query.split("&");

    for (String pair : pairs) {

      String[] parts = pair.split("=", 2);

      String key = URLDecoder.decode(parts[0], "UTF-8");

      String value = parts.length > 1
              ? URLDecoder.decode(parts[1], "UTF-8")
              : "";

      map.put(key, value);
    }

    return map;
  }
}