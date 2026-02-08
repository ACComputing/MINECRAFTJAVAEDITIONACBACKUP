/*
 * ╔═══════════════════════════════════════════════════════════════════╗
 * ║  Minecraft Launcher v0.1 — Bedrock-Style Launcher                ║
 * ║  Dark GUI · 900×560 · Offline Profiles                           ║
 * ║  Supports LAN & offline-mode servers (online-mode=false)         ║
 * ║                                                                   ║
 * ║  BUILD:  javac MinecraftLauncher.java                             ║
 * ║  RUN:    java MinecraftLauncher                                   ║
 * ║  JAR:    jar cfe minecraft.jar MinecraftLauncher MinecraftLauncher*.class ║
 * ║  Requires: Java 8+   No external dependencies.                   ║
 * ╚═══════════════════════════════════════════════════════════════════╝
 */

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;
import java.util.zip.*;

public class MinecraftLauncher extends JFrame {

    // ═══════════════════════════════════════════════════════════════════
    //  COLOURS — Bedrock Launcher palette
    // ═══════════════════════════════════════════════════════════════════
    static final Color C_BG         = new Color(28, 28, 28);
    static final Color C_SIDEBAR    = new Color(38, 38, 38);
    static final Color C_SIDEBAR_HI = new Color(55, 55, 55);
    static final Color C_SIDEBAR_SEL= new Color(65, 65, 65);
    static final Color C_TOPBAR     = new Color(44, 44, 44);
    static final Color C_FIELD      = new Color(50, 50, 50);
    static final Color C_HOVER      = new Color(62, 62, 62);
    static final Color C_POPUP      = new Color(42, 42, 42);
    static final Color C_GREEN      = new Color(58, 170, 53);
    static final Color C_GREEN_HI   = new Color(72, 195, 65);
    static final Color C_GREEN_DIM  = new Color(40, 120, 36);
    static final Color C_WHITE      = new Color(240, 240, 240);
    static final Color C_GREY       = new Color(170, 170, 170);
    static final Color C_DIM        = new Color(110, 110, 110);
    static final Color C_BORDER     = new Color(55, 55, 55);
    static final Color C_CONSOLE    = new Color(18, 18, 18);
    static final Color C_CON_TEXT   = new Color(80, 220, 100);
    static final Color C_TAB_SEL    = new Color(240, 240, 240);
    static final Color C_TAB_LINE   = new Color(58, 170, 53);

    static final String APP  = "Minecraft Launcher";
    static final String VER  = "0.1";
    static final String MANIFEST_URL =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    static Path ROOT, VER_DIR, LIB_DIR, ASS_DIR, NAT_DIR, PROF_FILE;

    // State — UI
    JTextField     tfUser, tfRam, tfJava;
    DarkDropdown   ddType, ddVersion;
    JTextArea      taLog;
    JButton        btnPlay;
    JProgressBar   progBar;
    JLabel         lblStatus, lblVerCount;
    JPanel         contentCards, sidebarPanel;
    CardLayout     contentLay;
    String         curSideNav  = "play";       // sidebar selection
    String         curTopTab   = "play";       // top-tab within "play"
    volatile boolean launching = false;

    // [id, type, url]
    final List<String[]> versions = Collections.synchronizedList(new ArrayList<>());

    // ═══════════════════════════════════════════════════════════════════
    //  MAIN
    // ═══════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        initDirs();
        nukeWhiteDefaults();
        SwingUtilities.invokeLater(() -> {
            MinecraftLauncher app = new MinecraftLauncher();
            app.setVisible(true);
            app.fetchManifest();
        });
    }

    static void nukeWhiteDefaults() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}
        Object[][] defs = {
            {"Panel.background",C_BG},{"Panel.foreground",C_WHITE},
            {"Label.foreground",C_WHITE},
            {"ComboBox.background",C_FIELD},{"ComboBox.foreground",C_WHITE},
            {"ComboBox.selectionBackground",C_GREEN},{"ComboBox.selectionForeground",C_WHITE},
            {"ComboBox.buttonBackground",C_FIELD},{"ComboBox.buttonDarkShadow",C_BORDER},
            {"ComboBox.buttonHighlight",C_HOVER},{"ComboBox.buttonShadow",C_BORDER},
            {"List.background",C_POPUP},{"List.foreground",C_WHITE},
            {"List.selectionBackground",C_GREEN},{"List.selectionForeground",C_WHITE},
            {"ScrollBar.background",C_BG},{"ScrollBar.thumb",C_HOVER},
            {"ScrollBar.track",C_BG},{"ScrollBar.width",10},
            {"ScrollPane.background",C_BG},
            {"TextField.background",C_FIELD},{"TextField.foreground",C_WHITE},
            {"TextField.caretForeground",C_WHITE},
            {"TextArea.background",C_CONSOLE},{"TextArea.foreground",C_CON_TEXT},
            {"ToolTip.background",C_POPUP},{"ToolTip.foreground",C_WHITE},
            {"OptionPane.background",C_BG},{"OptionPane.foreground",C_WHITE},
            {"OptionPane.messageForeground",C_WHITE},
            {"Button.background",C_FIELD},{"Button.foreground",C_WHITE},
            {"ProgressBar.background",C_FIELD},{"ProgressBar.foreground",C_GREEN},
            {"PopupMenu.background",C_POPUP},{"PopupMenu.foreground",C_WHITE},
            {"PopupMenu.border",BorderFactory.createLineBorder(C_BORDER)},
            {"MenuItem.background",C_POPUP},{"MenuItem.foreground",C_WHITE},
            {"MenuItem.selectionBackground",C_GREEN},{"MenuItem.selectionForeground",C_WHITE},
            {"Viewport.background",C_BG},
            {"Separator.foreground",C_BORDER},{"Separator.background",C_BG},
            {"CheckBox.background",C_BG},{"CheckBox.foreground",C_WHITE},
        };
        for (Object[] d : defs) UIManager.put(d[0], d[1]);
    }

    static void initDirs() {
        String os = System.getProperty("os.name","").toLowerCase();
        Path home;
        if (os.contains("win"))      home = Paths.get(System.getenv("APPDATA"),".minecraft");
        else if (os.contains("mac")) home = Paths.get(System.getProperty("user.home"),
                                              "Library","Application Support","minecraft");
        else                         home = Paths.get(System.getProperty("user.home"),".minecraft");
        ROOT      = home;
        VER_DIR   = home.resolve("versions");
        LIB_DIR   = home.resolve("libraries");
        ASS_DIR   = home.resolve("assets");
        NAT_DIR   = home.resolve("natives");
        PROF_FILE = home.resolve("profiles.properties");
        try {
            Files.createDirectories(VER_DIR);
            Files.createDirectories(LIB_DIR);
            Files.createDirectories(ASS_DIR.resolve("indexes"));
            Files.createDirectories(ASS_DIR.resolve("objects"));
            Files.createDirectories(NAT_DIR);
        } catch (IOException e) { System.err.println(e); }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  DARK DROPDOWN
    // ═══════════════════════════════════════════════════════════════════
    class DarkDropdown extends JPanel {
        final List<String> items = new ArrayList<>();
        private int selectedIdx = -1;
        private final JLabel display;
        private final JLabel arrow;
        private JPopupMenu popup;
        private final List<Runnable> listeners = new ArrayList<>();

        DarkDropdown() {
            setLayout(new BorderLayout());
            setBackground(C_FIELD);
            setBorder(BorderFactory.createLineBorder(C_BORDER));
            setPreferredSize(new Dimension(0, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(true);

            display = new JLabel("Loading...");
            display.setForeground(C_WHITE);
            display.setFont(new Font("SansSerif", Font.PLAIN, 12));
            display.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
            display.setOpaque(false);
            add(display, BorderLayout.CENTER);

            arrow = new JLabel(" \u25BC ") {
                @Override protected void paintComponent(Graphics g) {
                    g.setColor(C_FIELD); g.fillRect(0,0,getWidth(),getHeight());
                    super.paintComponent(g);
                }
            };
            arrow.setForeground(C_GREY); arrow.setFont(new Font("SansSerif",Font.PLAIN,10));
            arrow.setOpaque(true); arrow.setBackground(C_FIELD);
            add(arrow, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { showPopup(); }
                public void mouseEntered(MouseEvent e) { setBackground(C_HOVER); }
                public void mouseExited(MouseEvent e)  { setBackground(C_FIELD); }
            });
        }

        void setItems(List<String> ni) {
            items.clear(); items.addAll(ni);
            if (!items.isEmpty()) { selectedIdx=0; display.setText(items.get(0)); display.setForeground(C_WHITE); }
            else { selectedIdx=-1; display.setText("(none)"); display.setForeground(C_DIM); }
            popup = null;
        }
        void setSelected(String v) {
            for (int i=0;i<items.size();i++) if(items.get(i).equals(v)){selectedIdx=i;display.setText(v);display.setForeground(C_WHITE);return;}
        }
        String getSelected() { return (selectedIdx>=0&&selectedIdx<items.size())?items.get(selectedIdx):null; }
        void addChangeListener(Runnable r) { listeners.add(r); }

        private void showPopup() { if(items.isEmpty())return; buildPopup(); popup.show(this,0,getHeight()); }

        private void buildPopup() {
            popup=new JPopupMenu(); popup.setBackground(C_POPUP);
            popup.setBorder(BorderFactory.createLineBorder(C_BORDER)); popup.setOpaque(true);
            JPanel list=new JPanel(); list.setLayout(new BoxLayout(list,BoxLayout.Y_AXIS));
            list.setBackground(C_POPUP); list.setOpaque(true);
            for(int i=0;i<items.size();i++){
                final int idx=i; final String text=items.get(i);
                JPanel row=new JPanel(new BorderLayout()){
                    @Override protected void paintComponent(Graphics g){g.setColor(getBackground());g.fillRect(0,0,getWidth(),getHeight());super.paintComponent(g);}
                };
                row.setBackground(idx==selectedIdx?C_GREEN:C_POPUP); row.setOpaque(true);
                row.setPreferredSize(new Dimension(0,26)); row.setMaximumSize(new Dimension(Integer.MAX_VALUE,26));
                JLabel lbl=new JLabel(text); lbl.setForeground(C_WHITE);
                lbl.setFont(new Font("SansSerif",Font.PLAIN,12));
                lbl.setBorder(BorderFactory.createEmptyBorder(0,10,0,10)); lbl.setOpaque(false);
                row.add(lbl);
                row.addMouseListener(new MouseAdapter(){
                    public void mouseEntered(MouseEvent e){row.setBackground(C_GREEN);}
                    public void mouseExited(MouseEvent e){row.setBackground(idx==selectedIdx?C_GREEN_HI:C_POPUP);}
                    public void mousePressed(MouseEvent e){selectedIdx=idx;display.setText(text);display.setForeground(C_WHITE);popup.setVisible(false);for(Runnable r:listeners)r.run();}
                });
                list.add(row);
            }
            JScrollPane sp=new JScrollPane(list); sp.setBackground(C_POPUP);
            sp.getViewport().setBackground(C_POPUP); sp.setBorder(null);
            sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            sp.getVerticalScrollBar().setUnitIncrement(26); darkScrollBar(sp.getVerticalScrollBar());
            int vis=Math.min(items.size(),18); int popH=vis*26+4;
            sp.setPreferredSize(new Dimension(getWidth()-2,popH));
            popup.setLayout(new BorderLayout()); popup.add(sp,BorderLayout.CENTER);
            popup.setPopupSize(getWidth(),popH+2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR — Bedrock-style layout
    // ═══════════════════════════════════════════════════════════════════
    MinecraftLauncher() {
        setTitle(APP + " v" + VER);
        setSize(900, 560);
        setMinimumSize(new Dimension(800, 500));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(0, 0));
        getContentPane().setBackground(C_BG);

        // Main structure: [sidebar | right-panel]
        add(buildSidebar(), BorderLayout.WEST);

        // Right panel: content area
        JPanel right = new JPanel(new BorderLayout(0, 0));
        right.setBackground(C_BG);
        right.setOpaque(true);
        right.add(buildContentArea(), BorderLayout.CENTER);
        right.add(buildBottomBar(),   BorderLayout.SOUTH);
        add(right, BorderLayout.CENTER);

        loadProfile();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  SIDEBAR — Bedrock-style wide nav
    // ═══════════════════════════════════════════════════════════════════
    JPanel buildSidebar() {
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBackground(C_SIDEBAR);
        sidebarPanel.setPreferredSize(new Dimension(200, 0));
        sidebarPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, C_BORDER));
        sidebarPanel.setOpaque(true);

        // ── Profile area ──
        JPanel profile = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(C_SIDEBAR); g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        profile.setOpaque(false);
        profile.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        profile.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        // Avatar placeholder — Minecraft grass block
        JPanel avatar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Dirt base
                g2.setColor(new Color(134, 96, 67));
                g2.fillRoundRect(0, 0, 36, 36, 4, 4);
                // Grass top
                g2.setColor(new Color(89, 168, 44));
                g2.fillRoundRect(0, 0, 36, 14, 4, 4);
                g2.fillRect(0, 8, 36, 6);
                // Pixel detail
                g2.setColor(new Color(100, 75, 50));
                for (int i = 0; i < 4; i++) {
                    g2.fillRect(4 + i * 8, 18, 4, 4);
                    g2.fillRect(8 + i * 7, 26, 3, 3);
                }
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(36, 36); }
        };
        avatar.setOpaque(false);
        profile.add(avatar, BorderLayout.WEST);

        JPanel namePanel = new JPanel();
        namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.Y_AXIS));
        namePanel.setOpaque(false);
        namePanel.add(Box.createVerticalStrut(2));
        JLabel nameL = new JLabel("Player");
        nameL.setForeground(C_WHITE); nameL.setFont(new Font("SansSerif", Font.BOLD, 13));
        namePanel.add(nameL);
        JLabel subL = new JLabel("Profile");
        subL.setForeground(C_DIM); subL.setFont(new Font("SansSerif", Font.PLAIN, 10));
        namePanel.add(subL);
        profile.add(namePanel, BorderLayout.CENTER);

        sidebarPanel.add(profile);
        sidebarPanel.add(sidebarSep());

        // ── Nav items ──
        sidebarPanel.add(Box.createVerticalStrut(6));
        sidebarPanel.add(sideNavItem("\uD83D\uDCF0  News",         "news"));
        sidebarPanel.add(Box.createVerticalStrut(2));
        sidebarPanel.add(sideNavItem("\uD83C\uDFAE  Play",         "play"));
        sidebarPanel.add(Box.createVerticalStrut(2));
        sidebarPanel.add(sideNavItem("\u2699  Settings",           "settings"));
        sidebarPanel.add(Box.createVerticalStrut(2));
        sidebarPanel.add(sideNavItem("\uD83D\uDCBB  Console",      "console"));

        sidebarPanel.add(Box.createVerticalGlue());

        // ── Bottom: Community ──
        sidebarPanel.add(sidebarSep());
        JLabel comm = new JLabel("  Marketplace");
        comm.setForeground(C_GREEN); comm.setFont(new Font("SansSerif", Font.BOLD, 12));
        comm.setBorder(BorderFactory.createEmptyBorder(8, 14, 10, 0));
        comm.setAlignmentX(LEFT_ALIGNMENT);
        sidebarPanel.add(comm);

        return sidebarPanel;
    }

    JPanel sidebarSep() {
        JPanel sep = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(C_BORDER); g.fillRect(12, 0, getWidth()-24, 1);
            }
        };
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setPreferredSize(new Dimension(0, 1));
        sep.setOpaque(false);
        return sep;
    }

    JPanel sideNavItem(String text, String nav) {
        JPanel item = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = nav.equals(curSideNav) ? C_SIDEBAR_SEL :
                           getBackground().equals(C_SIDEBAR_HI) ? C_SIDEBAR_HI : C_SIDEBAR;
                g2.setColor(bg);
                g2.fillRoundRect(6, 0, getWidth()-12, getHeight(), 6, 6);
                // Green left indicator for selected
                if (nav.equals(curSideNav)) {
                    g2.setColor(C_GREEN);
                    g2.fillRoundRect(6, 4, 3, getHeight()-8, 2, 2);
                }
                g2.dispose();
            }
        };
        item.setOpaque(false);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        item.setPreferredSize(new Dimension(0, 36));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel lbl = new JLabel(text);
        lbl.setForeground(nav.equals(curSideNav) ? C_WHITE : C_GREY);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        lbl.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 0));
        item.add(lbl, BorderLayout.CENTER);

        item.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { item.setBackground(C_SIDEBAR_HI); item.repaint(); }
            public void mouseExited(MouseEvent e)  { item.setBackground(C_SIDEBAR); item.repaint(); }
            public void mousePressed(MouseEvent e) {
                if (!nav.equals("news")) { // news is placeholder
                    curSideNav = nav;
                    contentLay.show(contentCards, nav);
                    sidebarPanel.repaint();
                }
            }
        });
        return item;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CONTENT AREA — card-switched panels
    // ═══════════════════════════════════════════════════════════════════
    JPanel buildContentArea() {
        contentCards = new JPanel();
        contentLay = new CardLayout();
        contentCards.setLayout(contentLay);
        contentCards.setBackground(C_BG);
        contentCards.setOpaque(true);

        contentCards.add(buildPlayPanel(),    "play");
        contentCards.add(buildSettingsPanel(),"settings");
        contentCards.add(buildConsolePanel(), "console");
        contentCards.add(buildNewsPanel(),    "news");
        return contentCards;
    }

    // ─── PLAY PANEL — hero banner + top tabs + config ───────────────
    JPanel buildPlayPanel() {
        JPanel p = new JPanel(new BorderLayout(0, 0));
        p.setBackground(C_BG); p.setOpaque(true);

        // Top tab bar
        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabBar.setBackground(C_TOPBAR); tabBar.setOpaque(true);
        tabBar.setPreferredSize(new Dimension(0, 38));
        tabBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER));

        // Header label
        JLabel hdr = new JLabel("  MINECRAFT");
        hdr.setForeground(C_WHITE); hdr.setFont(new Font("SansSerif", Font.BOLD, 12));
        hdr.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 20));
        tabBar.add(hdr);

        String[] tabs = {"Play", "Installations", "Patch Notes"};
        for (String t : tabs) {
            tabBar.add(topTabBtn(t));
        }
        p.add(tabBar, BorderLayout.NORTH);

        // Main content: hero + form
        JPanel main = new JPanel(new BorderLayout(0, 0));
        main.setBackground(C_BG); main.setOpaque(true);

        // ── Hero banner ──
        JPanel hero = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();

                // Sky gradient
                g2.setPaint(new GradientPaint(0, 0, new Color(135, 190, 245),
                                              0, h, new Color(80, 160, 80)));
                g2.fillRect(0, 0, w, h);

                // Sun
                g2.setColor(new Color(255, 240, 120, 180));
                g2.fillOval(w - 120, 15, 70, 70);

                // Clouds
                g2.setColor(new Color(255, 255, 255, 90));
                Random rng = new Random(77);
                for (int i = 0; i < 6; i++) {
                    int cx = rng.nextInt(w), cy = 10 + rng.nextInt(h/3);
                    g2.fillRoundRect(cx, cy, 80 + rng.nextInt(60), 16 + rng.nextInt(10), 12, 12);
                }

                // Rolling hills
                int hillBase = h * 2 / 3;
                g2.setColor(new Color(60, 140, 50));
                for (int x = 0; x < w; x++) {
                    double y = hillBase + Math.sin(x * 0.012) * 20 + Math.sin(x * 0.025 + 2) * 12;
                    g2.fillRect(x, (int) y, 1, h - (int) y);
                }
                g2.setColor(new Color(50, 120, 42));
                for (int x = 0; x < w; x++) {
                    double y = hillBase + 18 + Math.sin(x * 0.018 + 1) * 15 + Math.sin(x * 0.04) * 8;
                    g2.fillRect(x, (int) y, 1, h - (int) y);
                }

                // Blocky trees
                rng = new Random(42);
                g2.setColor(new Color(35, 90, 30));
                for (int i = 0; i < 12; i++) {
                    int tx = rng.nextInt(w);
                    int ty = hillBase - 10 + (int)(Math.sin(tx*0.012)*20) - rng.nextInt(30);
                    int ts = 14 + rng.nextInt(12);
                    // trunk
                    g2.setColor(new Color(90, 60, 30));
                    g2.fillRect(tx + ts/2 - 2, ty + ts - 4, 5, 14);
                    // leaves (blocky)
                    g2.setColor(new Color(30 + rng.nextInt(30), 80 + rng.nextInt(40), 25));
                    g2.fillRect(tx, ty, ts, ts);
                    g2.fillRect(tx + 3, ty - 6, ts - 6, 8);
                }

                // Ground blocks
                g2.setColor(new Color(100, 70, 40));
                int groundY = h - 20;
                for (int x = 0; x < w; x += 8) {
                    g2.fillRect(x, groundY, 7, 20);
                }
                g2.setColor(new Color(70, 130, 50));
                for (int x = 0; x < w; x += 8) {
                    g2.fillRect(x, groundY - 3, 7, 5);
                }

                // Title text
                g2.setColor(new Color(0, 0, 0, 60));
                g2.setFont(new Font("SansSerif", Font.BOLD, 36));
                FontMetrics fm = g2.getFontMetrics();
                String title = "MINECRAFT";
                int tx = (w - fm.stringWidth(title))/2;
                int ty2 = h/2 - 10;
                g2.drawString(title, tx + 2, ty2 + 2);
                g2.setColor(C_WHITE);
                g2.drawString(title, tx, ty2);

                // Subtitle
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                g2.setColor(new Color(255, 255, 255, 200));
                fm = g2.getFontMetrics();
                String sub = "BEDROCK EDITION";
                g2.drawString(sub, (w - fm.stringWidth(sub))/2, ty2 + 24);

                // Version badge
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.setColor(new Color(255, 255, 255, 140));
                fm = g2.getFontMetrics();
                String vt = "v" + VER + " \u2022 Offline Mode";
                g2.drawString(vt, (w - fm.stringWidth(vt))/2, ty2 + 42);

                g2.dispose();
            }
        };
        hero.setPreferredSize(new Dimension(0, 200));
        hero.setOpaque(false);
        main.add(hero, BorderLayout.NORTH);

        // ── Config form below hero ──
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(C_BG); form.setOpaque(true);
        form.setBorder(BorderFactory.createEmptyBorder(12, 20, 8, 20));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Username
        gc.gridy=0; gc.gridx=0; gc.weightx=0;
        form.add(lbl("Username", C_GREY, 12, false), gc);
        gc.gridx=1; gc.weightx=1;
        tfUser = dkField("Player");
        form.add(tfUser, gc);

        // Row 0 right: RAM
        gc.gridx=2; gc.weightx=0;
        form.add(lbl("  RAM (MB)", C_GREY, 12, false), gc);
        gc.gridx=3; gc.weightx=0.4;
        tfRam = dkField("2048");
        form.add(tfRam, gc);

        // Row 1: Type + Version
        gc.gridy=1; gc.gridx=0; gc.weightx=0;
        form.add(lbl("Type", C_GREY, 12, false), gc);
        gc.gridx=1; gc.weightx=1;
        ddType = new DarkDropdown();
        ddType.setItems(Arrays.asList("release","snapshot","old_beta","old_alpha","all"));
        ddType.addChangeListener(this::filterVersions);
        form.add(ddType, gc);

        gc.gridx=2; gc.weightx=0;
        JPanel vp = dk(new FlowLayout(FlowLayout.LEFT, 0, 0));
        vp.add(lbl("  Version", C_GREY, 12, false));
        lblVerCount = lbl("", C_DIM, 10, false);
        lblVerCount.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 0));
        vp.add(lblVerCount);
        form.add(vp, gc);
        gc.gridx=3; gc.weightx=0.4;
        ddVersion = new DarkDropdown();
        form.add(ddVersion, gc);

        // Progress bar
        gc.gridy=2; gc.gridx=0; gc.gridwidth=4; gc.weightx=1;
        gc.insets = new Insets(8, 6, 4, 6);
        progBar = new JProgressBar(0, 100);
        progBar.setPreferredSize(new Dimension(0, 4));
        progBar.setBackground(C_FIELD); progBar.setForeground(C_GREEN);
        progBar.setBorderPainted(false); progBar.setStringPainted(false);
        progBar.setOpaque(true);
        form.add(progBar, gc);

        main.add(form, BorderLayout.CENTER);
        p.add(main, BorderLayout.CENTER);
        return p;
    }

    JButton topTabBtn(String text) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(C_TOPBAR);
                g2.fillRect(0, 0, getWidth(), getHeight());
                boolean sel = text.equalsIgnoreCase(curTopTab) ||
                              (text.equals("Play") && curTopTab.equals("play"));
                g2.setFont(getFont());
                g2.setColor(sel ? C_TAB_SEL : C_DIM);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, (getWidth()-fm.stringWidth(text))/2,
                              (getHeight()+fm.getAscent()-fm.getDescent())/2);
                if (sel) {
                    g2.setColor(C_TAB_LINE);
                    g2.fillRect(8, getHeight()-3, getWidth()-16, 3);
                }
                g2.dispose();
            }
        };
        b.setFont(new Font("SansSerif", Font.PLAIN, 12));
        b.setPreferredSize(new Dimension(100, 38));
        b.setFocusPainted(false); b.setBorderPainted(false);
        b.setContentAreaFilled(false); b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> { curTopTab = text.toLowerCase(); sidebarPanel.repaint(); b.getParent().repaint(); });
        return b;
    }

    // ─── SETTINGS PANEL ─────────────────────────────────────────────
    JPanel buildSettingsPanel() {
        JPanel p = dk(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;

        gc.gridx=0; gc.gridy=0; gc.gridwidth=2;
        p.add(lbl("Settings", C_WHITE, 18, true), gc);

        gc.gridy=1; gc.gridwidth=1; gc.weightx=0;
        p.add(lbl("Java Path", C_GREY, 12, false), gc);
        gc.gridx=1; gc.weightx=1;
        tfJava = dkField("java");
        p.add(tfJava, gc);

        gc.gridy=2; gc.gridx=0; gc.weightx=0;
        p.add(lbl("Game Dir", C_GREY, 12, false), gc);
        gc.gridx=1; gc.weightx=1;
        JTextField td = dkField(ROOT.toString());
        td.setEditable(false); td.setForeground(C_DIM);
        p.add(td, gc);

        gc.gridy=3; gc.gridx=0; gc.gridwidth=2;
        JButton bo = mkBtn("Open Game Folder");
        bo.addActionListener(e -> {
            try { Desktop.getDesktop().open(ROOT.toFile()); }
            catch (Exception ex) { log("Err: " + ex.getMessage()); }
        });
        p.add(bo, gc);

        gc.gridy=4;
        JPanel info = new JPanel(new BorderLayout());
        info.setBackground(new Color(35, 35, 35));
        info.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(C_BORDER),
            BorderFactory.createEmptyBorder(12, 14, 12, 14)));
        info.setOpaque(true);
        JTextArea ta = new JTextArea(
            "How it works:\n\n"
            + "Creates offline profiles with a UUID\n"
            + "generated from your username (same as vanilla).\n\n"
            + "Join any server with online-mode=false,\n"
            + "and all LAN worlds.\n\n"
            + "Versions download from Mojang's public CDN.\n"
            + "Launch: --userType legacy --accessToken 0");
        ta.setEditable(false);
        ta.setBackground(new Color(35, 35, 35));
        ta.setForeground(C_GREY);
        ta.setFont(new Font("SansSerif", Font.PLAIN, 11));
        ta.setBorder(null); ta.setOpaque(true);
        info.add(ta);
        p.add(info, gc);

        gc.gridy=5; gc.weighty=1;
        p.add(Box.createVerticalGlue(), gc);
        return p;
    }

    // ─── CONSOLE PANEL ──────────────────────────────────────────────
    JPanel buildConsolePanel() {
        JPanel p = dk(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        p.add(lbl("Console Output", C_WHITE, 16, true), BorderLayout.NORTH);

        taLog = new JTextArea();
        taLog.setEditable(false); taLog.setBackground(C_CONSOLE);
        taLog.setForeground(C_CON_TEXT); taLog.setCaretColor(C_CON_TEXT);
        taLog.setFont(new Font("Monospaced", Font.PLAIN, 11));
        taLog.setLineWrap(true); taLog.setWrapStyleWord(true);
        taLog.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        taLog.setOpaque(true);

        JScrollPane sp = new JScrollPane(taLog);
        sp.setBorder(BorderFactory.createLineBorder(C_BORDER));
        sp.getViewport().setBackground(C_CONSOLE);
        sp.setBackground(C_BG); sp.setOpaque(true);
        darkScrollBar(sp.getVerticalScrollBar());
        darkScrollBar(sp.getHorizontalScrollBar());
        sp.getVerticalScrollBar().setUnitIncrement(14);
        p.add(sp, BorderLayout.CENTER);

        JPanel bot = dk(new FlowLayout(FlowLayout.RIGHT));
        JButton bc = mkBtn("Clear");
        bc.addActionListener(e -> taLog.setText(""));
        bot.add(bc);
        p.add(bot, BorderLayout.SOUTH);
        return p;
    }

    // ─── NEWS PANEL (placeholder) ───────────────────────────────────
    JPanel buildNewsPanel() {
        JPanel p = dk(new BorderLayout());
        JLabel nl = new JLabel("News — Coming soon", SwingConstants.CENTER);
        nl.setForeground(C_DIM); nl.setFont(new Font("SansSerif", Font.PLAIN, 16));
        p.add(nl, BorderLayout.CENTER);
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  BOTTOM BAR — Big green PLAY + status
    // ═══════════════════════════════════════════════════════════════════
    JPanel buildBottomBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(new Color(34, 34, 34));
        bar.setPreferredSize(new Dimension(0, 60));
        bar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, C_BORDER),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        bar.setOpaque(true);

        // Status left
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setOpaque(false);
        lblStatus = lbl("Ready", C_DIM, 11, false);
        statusPanel.add(lblStatus, BorderLayout.SOUTH);
        JLabel jvl = lbl("Java " + System.getProperty("java.version") + " \u2022 " + APP + " v" + VER, C_DIM, 10, false);
        statusPanel.add(jvl, BorderLayout.NORTH);
        bar.add(statusPanel, BorderLayout.CENTER);

        // Big green PLAY button — Bedrock style
        btnPlay = new JButton("PLAY") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = launching ? C_GREEN_DIM :
                           getModel().isRollover() ? C_GREEN_HI : C_GREEN;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                // Subtle top highlight
                g2.setColor(new Color(255, 255, 255, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight()/2, 6, 6);
                // Text
                g2.setFont(getFont());
                g2.setColor(C_WHITE);
                FontMetrics fm = g2.getFontMetrics();
                String t = getText();
                g2.drawString(t, (getWidth()-fm.stringWidth(t))/2,
                              (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        btnPlay.setFont(new Font("SansSerif", Font.BOLD, 18));
        btnPlay.setPreferredSize(new Dimension(200, 42));
        btnPlay.setFocusPainted(false); btnPlay.setBorderPainted(false);
        btnPlay.setContentAreaFilled(false); btnPlay.setOpaque(false);
        btnPlay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPlay.addActionListener(e -> doLaunch());
        bar.add(btnPlay, BorderLayout.EAST);

        return bar;
    }

    // Helper alias
    void setTab(String t) {
        curSideNav = t;
        contentLay.show(contentCards, t);
        sidebarPanel.repaint();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  WIDGET FACTORY
    // ═══════════════════════════════════════════════════════════════════
    JPanel dk(LayoutManager lm) {
        JPanel p = new JPanel(lm); p.setBackground(C_BG); p.setOpaque(true); return p;
    }
    JLabel lbl(String t, Color fg, int sz, boolean bold) {
        JLabel l = new JLabel(t); l.setForeground(fg);
        l.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, sz)); return l;
    }
    JTextField dkField(String text) {
        JTextField f = new JTextField(text);
        f.setBackground(C_FIELD); f.setForeground(C_WHITE); f.setCaretColor(C_WHITE);
        f.setSelectionColor(C_GREEN); f.setSelectedTextColor(C_WHITE);
        f.setFont(new Font("SansSerif",Font.PLAIN,12));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(C_BORDER),
            BorderFactory.createEmptyBorder(4,10,4,10)));
        f.setPreferredSize(new Dimension(0,30)); f.setOpaque(true); return f;
    }
    JButton mkBtn(String text) {
        JButton b = new JButton(text);
        b.setBackground(C_FIELD); b.setForeground(C_WHITE);
        b.setFont(new Font("SansSerif",Font.PLAIN,11));
        b.setFocusPainted(false); b.setOpaque(true);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(C_BORDER),
            BorderFactory.createEmptyBorder(6,16,6,16)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter(){
            public void mouseEntered(MouseEvent e){b.setBackground(C_HOVER);}
            public void mouseExited(MouseEvent e){b.setBackground(C_FIELD);}
        });
        return b;
    }

    static void darkScrollBar(JScrollBar sb) {
        if (sb == null) return;
        sb.setBackground(C_BG); sb.setOpaque(true);
        sb.setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = C_HOVER; thumbDarkShadowColor = C_BORDER;
                thumbHighlightColor = C_HOVER; thumbLightShadowColor = C_HOVER;
                trackColor = C_BG; trackHighlightColor = C_BG;
            }
            @Override protected JButton createDecreaseButton(int o) { return nub(); }
            @Override protected JButton createIncreaseButton(int o) { return nub(); }
        });
    }
    static JButton nub() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0,0));
        b.setMaximumSize(new Dimension(0,0));
        b.setMinimumSize(new Dimension(0,0));
        return b;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LOGGING
    // ═══════════════════════════════════════════════════════════════════
    void log(String msg) {
        String ts = String.format("[%tT] ", System.currentTimeMillis());
        SwingUtilities.invokeLater(() -> {
            if (taLog != null) {
                taLog.append(ts + msg + "\n");
                taLog.setCaretPosition(taLog.getDocument().getLength());
            }
        });
        System.out.println(msg);
    }
    void status(String s) { SwingUtilities.invokeLater(() -> lblStatus.setText(s)); }
    void prog(int v)      { SwingUtilities.invokeLater(() -> progBar.setValue(v)); }

    // ═══════════════════════════════════════════════════════════════════
    //  PROFILE
    // ═══════════════════════════════════════════════════════════════════
    void saveProfile() {
        try {
            Properties p = new Properties();
            p.setProperty("username", tfUser.getText().trim());
            p.setProperty("ram",      tfRam.getText().trim());
            p.setProperty("java",     tfJava.getText().trim());
            String sv = ddVersion.getSelected();
            String st = ddType.getSelected();
            if (sv != null) p.setProperty("version", sv);
            if (st != null) p.setProperty("type", st);
            try (OutputStream o = Files.newOutputStream(PROF_FILE)) {
                p.store(o, APP + " Profile");
            }
        } catch (Exception e) { log("Save err: " + e.getMessage()); }
    }
    void loadProfile() {
        if (!Files.exists(PROF_FILE)) return;
        try {
            Properties p = new Properties();
            try (InputStream i = Files.newInputStream(PROF_FILE)) { p.load(i); }
            if (p.containsKey("username")) tfUser.setText(p.getProperty("username"));
            if (p.containsKey("ram"))      tfRam.setText(p.getProperty("ram"));
            if (p.containsKey("java"))     tfJava.setText(p.getProperty("java"));
        } catch (Exception e) { log("Load err: " + e.getMessage()); }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  VERSION MANIFEST
    // ═══════════════════════════════════════════════════════════════════
    void fetchManifest() {
        new Thread(() -> {
            log("Fetching version manifest...");
            status("Downloading version list...");
            try {
                String json = httpGet(MANIFEST_URL);
                log("Manifest downloaded: " + json.length() + " bytes");
                parseManifest(json);
                log("Parsed " + versions.size() + " total versions");

                if (versions.isEmpty()) {
                    log("WARNING: 0 versions parsed!");
                    log("JSON starts with: " + json.substring(0, Math.min(200, json.length())));
                }
                int rel=0,snap=0,beta=0,alpha=0;
                synchronized(versions){for(String[]v:versions){switch(v[1]){
                    case"release":rel++;break;case"snapshot":snap++;break;
                    case"old_beta":beta++;break;case"old_alpha":alpha++;break;}}}
                log("  Releases: "+rel+"  Snapshots: "+snap+"  Betas: "+beta+"  Alphas: "+alpha);

                SwingUtilities.invokeAndWait(this::filterVersions);
                log("Version dropdown populated: " + ddVersion.items.size() + " shown");
                status("Ready - " + versions.size() + " versions");

                if (Files.exists(PROF_FILE)) {
                    Properties pr = new Properties();
                    try (InputStream in = Files.newInputStream(PROF_FILE)) { pr.load(in); }
                    String st = pr.getProperty("type"), sv = pr.getProperty("version");
                    if (st != null) SwingUtilities.invokeAndWait(() -> { ddType.setSelected(st); filterVersions(); });
                    if (sv != null) SwingUtilities.invokeAndWait(() -> ddVersion.setSelected(sv));
                }
            } catch (Exception e) {
                log("Manifest error: " + e.getMessage());
                status("Offline - check connection");
            }
        }, "manifest").start();
    }

    void parseManifest(String json) {
        versions.clear();
        int idx = json.indexOf("\"versions\""); if (idx < 0) return;
        int arrStart = json.indexOf('[', idx);   if (arrStart < 0) return;
        Pattern pat = Pattern.compile(
            "\"id\"\\s*:\\s*\"([^\"]+)\".*?\"type\"\\s*:\\s*\"([^\"]+)\".*?\"url\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL);
        int depth=0, objStart=-1;
        for (int i=arrStart+1; i<json.length(); i++) {
            char c=json.charAt(i);
            if(c=='{'){if(depth==0)objStart=i;depth++;}
            else if(c=='}'){depth--;if(depth==0&&objStart>=0){Matcher m=pat.matcher(json.substring(objStart,i+1));if(m.find())versions.add(new String[]{m.group(1),m.group(2),m.group(3)});objStart=-1;}}
            else if(c==']'&&depth==0)break;
        }
    }

    void filterVersions() {
        String type=ddType.getSelected(); if(type==null)type="release";
        List<String> filtered=new ArrayList<>();
        synchronized(versions){for(String[]v:versions)if(type.equals("all")||v[1].equals(type))filtered.add(v[0]);}
        ddVersion.setItems(filtered);
        lblVerCount.setText("("+filtered.size()+")");
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LAUNCH
    // ═══════════════════════════════════════════════════════════════════
    void doLaunch() {
        if (launching) return;
        String user = tfUser.getText().trim();
        if (user.isEmpty()) {
            JOptionPane.showMessageDialog(this,"Enter a username!",APP,JOptionPane.WARNING_MESSAGE); return;
        }
        String ver = ddVersion.getSelected();
        if (ver == null || ver.equals("Loading...")) {
            JOptionPane.showMessageDialog(this,"Select a version!",APP,JOptionPane.WARNING_MESSAGE); return;
        }
        String ram  = tfRam.getText().trim();
        String java = tfJava.getText().trim();
        if (java.isEmpty()) java = "java";

        saveProfile();
        setTab("console");
        launching = true;
        SwingUtilities.invokeLater(() -> { btnPlay.setText("LAUNCHING..."); btnPlay.repaint(); });

        final String fJava = java, fVer = ver;
        new Thread(() -> {
            try { launchGame(user, fVer, ram, fJava); }
            catch (Exception e) { log("LAUNCH ERROR: " + e.getMessage()); status("Launch failed!"); }
            finally {
                launching = false;
                SwingUtilities.invokeLater(() -> { btnPlay.setText("PLAY"); btnPlay.repaint(); });
            }
        }, "launch").start();
    }

    void launchGame(String user, String vid, String ram, String java) throws Exception {
        log("=== Minecraft Launcher ===========================");
        log("Version:  " + vid);
        log("Username: " + user + "  (offline/legacy)");
        log("RAM:      " + ram + " MB");

        status("Resolving..."); prog(5);
        String vUrl = null;
        synchronized (versions) {
            for (String[] v : versions) if (v[0].equals(vid)) { vUrl = v[2]; break; }
        }
        if (vUrl == null) throw new RuntimeException("Not in manifest: " + vid);

        Path vDir = VER_DIR.resolve(vid); Files.createDirectories(vDir);
        Path vjp = vDir.resolve(vid + ".json");
        if (!Files.exists(vjp)) {
            log("Downloading version metadata...");
            Files.write(vjp, httpGet(vUrl).getBytes(StandardCharsets.UTF_8));
        }
        String vj = new String(Files.readAllBytes(vjp), StandardCharsets.UTF_8);
        prog(10);

        status("Client jar...");
        Path cjar = vDir.resolve(vid + ".jar");
        if (!Files.exists(cjar)) {
            String cu = findClientUrl(vj, vid);
            if (cu != null) { log("Downloading client..."); download(cu, cjar); }
            else throw new RuntimeException("No client URL for " + vid);
        }
        log("Client: " + Files.size(cjar) / 1024 + " KB");
        prog(20);

        status("Libraries...");
        List<Path> libs = resolveLibs(vj);
        prog(55);

        status("Downloading assets...");
        String assetId = jn(vj, "assetIndex", "id", null);
        String assetUrl = jn(vj, "assetIndex", "url", null);
        String assetIndexJson = null;
        if (assetId != null && assetUrl != null) {
            Path af = ASS_DIR.resolve("indexes").resolve(assetId + ".json");
            if (!Files.exists(af)) {
                log("Downloading asset index: " + assetId);
                try { assetIndexJson = httpGet(assetUrl);
                      Files.write(af, assetIndexJson.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) { log("Asset idx err: " + e.getMessage()); }
            } else { assetIndexJson = new String(Files.readAllBytes(af), StandardCharsets.UTF_8); }
            if (assetIndexJson != null) downloadAssetObjects(assetIndexJson);
            log("Asset index: " + assetId);
        } else { assetId = js(vj, "assets"); if (assetId == null) assetId = "legacy"; }
        prog(65);

        status("Natives...");
        Path nd = NAT_DIR.resolve(vid); Files.createDirectories(nd);
        extractNatives(nd); prog(75);

        String sep = System.getProperty("path.separator");
        StringBuilder cp = new StringBuilder();
        for (Path l : libs) if (Files.exists(l)) cp.append(l.toAbsolutePath()).append(sep);
        cp.append(cjar.toAbsolutePath());

        String mc = js(vj, "mainClass");
        if (mc == null) mc = vid.startsWith("b1.") || vid.startsWith("a1.") || vid.startsWith("c0.")
            ? "net.minecraft.launchwrapper.Launch" : "net.minecraft.client.main.Minecraft";
        log("Main: " + mc);

        String uuid = offlineUUID(user);

        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        if (osName().equals("osx")) cmd.add("-XstartOnFirstThread");
        String javaVer = System.getProperty("java.version", "");
        if (!javaVer.startsWith("1.") && !javaVer.startsWith("8")) {
            try { int major = Integer.parseInt(javaVer.split("[^0-9]")[0]);
                  if (major >= 21) cmd.add("--enable-native-access=ALL-UNNAMED");
            } catch (NumberFormatException ignored) { cmd.add("--enable-native-access=ALL-UNNAMED"); }
        }
        cmd.add("-Xmx" + ram + "M"); cmd.add("-Xms256M");
        cmd.add("-Djava.library.path=" + nd.toAbsolutePath());
        cmd.add("-Dminecraft.launcher.brand=minecraft-launcher");
        cmd.add("-Dminecraft.launcher.version=" + VER);
        cmd.add("-cp"); cmd.add(cp.toString()); cmd.add(mc);

        String mca = js(vj, "minecraftArguments");
        if (mca != null) {
            mca = mca.replace("${auth_player_name}", user)
                .replace("${version_name}", vid)
                .replace("${game_directory}", ROOT.toAbsolutePath().toString())
                .replace("${assets_root}", ASS_DIR.toAbsolutePath().toString())
                .replace("${game_assets}", ASS_DIR.toAbsolutePath().toString())
                .replace("${assets_index_name}", assetId)
                .replace("${auth_uuid}", uuid)
                .replace("${auth_access_token}", "0")
                .replace("${user_properties}", "{}")
                .replace("${user_type}", "legacy")
                .replace("${version_type}", "release")
                .replace("${auth_session}", "0");
            for (String a : mca.split("\\s+")) if (!a.isEmpty()) cmd.add(a);
        } else {
            cmd.add("--username");    cmd.add(user);
            cmd.add("--version");     cmd.add(vid);
            cmd.add("--gameDir");     cmd.add(ROOT.toAbsolutePath().toString());
            cmd.add("--assetsDir");   cmd.add(ASS_DIR.toAbsolutePath().toString());
            cmd.add("--assetIndex");  cmd.add(assetId);
            cmd.add("--uuid");        cmd.add(uuid);
            cmd.add("--accessToken"); cmd.add("0");
            cmd.add("--userType");    cmd.add("legacy");
            cmd.add("--versionType"); cmd.add("release");
        }
        if (mc.contains("launchwrapper")) {
            cmd.add("--tweakClass");
            cmd.add("net.minecraft.launchwrapper.AlphaVanillaTweaker");
        }

        log("UUID: " + uuid);
        if (osName().equals("osx")) log("macOS: -XstartOnFirstThread enabled");
        prog(90);

        status("Starting...");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(ROOT.toFile()); pb.redirectErrorStream(true);
        Process proc = pb.start();
        prog(100); status("Running - PID " + proc.pid());
        log("=== PID: " + proc.pid() + " ===");

        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line; while ((line = br.readLine()) != null) log("[MC] " + line);
        }
        log("=== Exited (code " + proc.waitFor() + ") ===");
        status("Ready"); prog(0);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  CLIENT URL RESOLVER
    // ═══════════════════════════════════════════════════════════════════
    String findClientUrl(String vj, String vid) {
        String u = jdn(vj, "downloads", "client", "url");
        if (u != null && u.startsWith("http")) return u;
        Matcher m = Pattern.compile(
            "\"client\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"(https://[^\"]+\\.jar)\"",
            Pattern.DOTALL).matcher(vj);
        if (m.find()) return m.group(1);
        m = Pattern.compile("\"url\"\\s*:\\s*\"(https://piston-data\\.mojang\\.com/[^\"]+\\.jar)\"")
            .matcher(vj);
        if (m.find()) return m.group(1);
        String sha = jdn(vj, "downloads", "client", "sha1");
        if (sha != null) return "https://piston-data.mojang.com/v1/objects/" + sha + "/client.jar";
        u = "https://s3.amazonaws.com/Minecraft.Download/versions/" + vid + "/" + vid + ".jar";
        try {
            HttpURLConnection t = (HttpURLConnection) URI.create(u).toURL().openConnection();
            t.setRequestMethod("HEAD"); t.setConnectTimeout(5000);
            if (t.getResponseCode() == 200) { t.disconnect(); return u; }
            t.disconnect();
        } catch (Exception ignored) {}
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LIBRARY RESOLVER
    // ═══════════════════════════════════════════════════════════════════
    List<Path> resolveLibs(String vj) {
        List<Path> out = new ArrayList<>();
        String os = osName();
        int li = vj.indexOf("\"libraries\""); if (li < 0) return out;
        int as = vj.indexOf('[', li);         if (as < 0) return out;
        int depth=0, objS=-1;
        for(int i=as+1;i<vj.length();i++){char c=vj.charAt(i);
            if(c=='{'){if(depth==0)objS=i;depth++;}
            else if(c=='}'){depth--;if(depth==0&&objS>=0){resolveLib(vj.substring(objS,i+1),out,os);objS=-1;}}
            else if(c==']'&&depth==0)break;}
        log("Libraries: " + out.size());
        return out;
    }

    void resolveLib(String lib, List<Path> out, String os) {
        if (lib.contains("\"rules\"") && !rulesOk(lib, os)) return;
        String path = jn(lib, "artifact", "path", null);
        String url  = jn(lib, "artifact", "url",  null);
        if (path != null) {
            Path f = LIB_DIR.resolve(path.replace("/", File.separator));
            out.add(f);
            if (!Files.exists(f) && url != null) {
                try { Files.createDirectories(f.getParent()); download(url, f);
                      log("  > " + f.getFileName()); }
                catch (Exception e) { log("  x " + e.getMessage()); }
            }
        }
        if (path == null && lib.contains("\"name\"")) {
            String name = js(lib, "name");
            if (name != null) {
                String mp = maven(name);
                if (mp != null) {
                    String ub = js(lib, "url");
                    Path f = LIB_DIR.resolve(mp.replace("/", File.separator));
                    out.add(f);
                    if (!Files.exists(f)) {
                        String[] repos = { ub, "https://libraries.minecraft.net/",
                                           "https://repo1.maven.org/maven2/" };
                        boolean ok = false;
                        for (String r : repos) {
                            if (r == null) continue; if (!r.endsWith("/")) r += "/";
                            try { Files.createDirectories(f.getParent());
                                  download(r + mp, f); log("  > " + f.getFileName()); ok = true; break; }
                            catch (Exception ignored) {}
                        }
                        if (!ok) log("  x " + name);
                    }
                }
            }
        }
        String nk = "natives-" + os;
        if (lib.contains("\"classifiers\"") && lib.contains(nk)) {
            String np = clf(lib, nk, "path"), nu = clf(lib, nk, "url");
            if (np != null) {
                Path nf = LIB_DIR.resolve(np.replace("/", File.separator));
                out.add(nf);
                if (!Files.exists(nf) && nu != null) {
                    try { Files.createDirectories(nf.getParent()); download(nu, nf);
                          log("  > [n] " + nf.getFileName()); }
                    catch (Exception e) { log("  x native: " + e.getMessage()); }
                }
            }
        }
    }

    String maven(String c) {
        String[] p = c.split(":"); if (p.length < 3) return null;
        return p[0].replace('.','/')+"/"+p[1]+"/"+p[2]+"/"+p[1]+"-"+p[2]+".jar";
    }

    boolean rulesOk(String j, String os) {
        boolean ok = false;
        Matcher m = Pattern.compile("\"action\"\\s*:\\s*\"(allow|disallow)\"").matcher(j);
        while (m.find()) {
            String a = m.group(1);
            int end = Math.min(m.end()+200, j.length());
            String ctx = j.substring(m.start(), end);
            if (ctx.contains("\"os\"")) { if (ctx.contains("\""+os+"\"")) ok = a.equals("allow"); }
            else ok = a.equals("allow");
        }
        return ok;
    }

    void extractNatives(Path nd) {
        try {
            Files.walk(LIB_DIR).filter(p -> p.toString().endsWith(".jar") &&
                (p.toString().contains("native") || p.toString().contains("lwjgl")))
            .forEach(jar -> {
                try (ZipInputStream z = new ZipInputStream(Files.newInputStream(jar))) {
                    ZipEntry e;
                    while ((e = z.getNextEntry()) != null) {
                        String n = e.getName();
                        if (n.endsWith(".dll")||n.endsWith(".so")||n.endsWith(".dylib")||n.endsWith(".jnilib")) {
                            Path o = nd.resolve(Paths.get(n).getFileName().toString());
                            if (!Files.exists(o)) Files.copy(z, o);
                        }
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception e) { log("Natives: " + e.getMessage()); }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  ASSET OBJECT DOWNLOADER — 64-thread parallel blitz
    // ═══════════════════════════════════════════════════════════════════
    static final String RESOURCES_URL = "https://resources.download.minecraft.net/";

    void downloadAssetObjects(String indexJson) {
        Pattern hashPat = Pattern.compile("\"hash\"\\s*:\\s*\"([0-9a-f]{40})\"");
        Matcher m = hashPat.matcher(indexJson);
        List<String> allHashes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (m.find()) { String h=m.group(1); if(seen.add(h))allHashes.add(h); }
        if (allHashes.isEmpty()) { log("No asset objects in index"); return; }

        Path objBase = ASS_DIR.resolve("objects");
        for (int i=0;i<256;i++) {
            try { Files.createDirectories(objBase.resolve(String.format("%02x",i))); }
            catch (Exception ignored) {}
        }

        List<String> needed = new ArrayList<>();
        for (String h : allHashes)
            if (!Files.exists(objBase.resolve(h.substring(0,2)).resolve(h))) needed.add(h);

        log("Assets: "+allHashes.size()+" total, "+needed.size()+" to download");
        if (needed.isEmpty()) { log("All assets cached!"); return; }

        int nThreads = Math.min(64, needed.size());
        ExecutorService pool = Executors.newFixedThreadPool(nThreads);
        AtomicInteger done = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicLong bytes = new AtomicLong(0);
        int total = needed.size();
        long startMs = System.currentTimeMillis();

        Thread ticker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                int d=done.get(); if(d>=total)break;
                long mb=bytes.get()/(1024*1024);
                long elapsed=Math.max(1,(System.currentTimeMillis()-startMs)/1000);
                status("Assets: "+d+"/"+total+" ("+mb+" MB, "+(mb/elapsed)+" MB/s)");
                try{Thread.sleep(200);}catch(InterruptedException e){break;}
            }
        }, "asset-ticker");
        ticker.setDaemon(true); ticker.start();

        CountDownLatch latch = new CountDownLatch(needed.size());
        for (String hash : needed) {
            pool.submit(() -> {
                try { String pre=hash.substring(0,2);
                      Path file=objBase.resolve(pre).resolve(hash);
                      bytes.addAndGet(fastDownload(RESOURCES_URL+pre+"/"+hash,file));
                } catch (Exception e) { failed.incrementAndGet(); }
                done.incrementAndGet(); latch.countDown();
            });
        }

        try{latch.await();}catch(InterruptedException ignored){}
        pool.shutdown(); ticker.interrupt();

        long elapsed=System.currentTimeMillis()-startMs;
        long totalMB=bytes.get()/(1024*1024);
        log("Assets complete: "+done.get()+" files, "+totalMB+" MB in "
            +(elapsed/1000)+"."+(elapsed%1000/100)+"s"
            +(failed.get()>0?" ("+failed.get()+" failed)":""));
    }

    long fastDownload(String urlStr, Path dest) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent","MinecraftLauncher/"+VER);
        c.setRequestProperty("Connection","keep-alive");
        long total=0;
        try(InputStream in=c.getInputStream();OutputStream out=Files.newOutputStream(dest)){
            byte[]buf=new byte[32768];int n;
            while((n=in.read(buf))!=-1){out.write(buf,0,n);total+=n;}
        }
        return total;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UUID
    // ═══════════════════════════════════════════════════════════════════
    static String offlineUUID(String u) {
        try {
            byte[] h = MessageDigest.getInstance("MD5")
                .digest(("OfflinePlayer:" + u).getBytes(StandardCharsets.UTF_8));
            h[6]=(byte)(h[6]&0x0f|0x30); h[8]=(byte)(h[8]&0x3f|0x80);
            StringBuilder sb=new StringBuilder();
            for(int i=0;i<16;i++){sb.append(String.format("%02x",h[i]));if(i==3||i==5||i==7||i==9)sb.append('-');}
            return sb.toString();
        } catch (Exception e) { return UUID.randomUUID().toString(); }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  HTTP
    // ═══════════════════════════════════════════════════════════════════
    String httpGet(String u) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(u).toURL().openConnection();
        c.setRequestProperty("User-Agent", APP+"/"+VER);
        c.setConnectTimeout(15000); c.setReadTimeout(30000);
        try (InputStream in=c.getInputStream(); ByteArrayOutputStream b=new ByteArrayOutputStream()) {
            byte[]buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)b.write(buf,0,n);
            return b.toString("UTF-8");
        }
    }
    void download(String u, Path d) throws IOException {
        Files.createDirectories(d.getParent());
        HttpURLConnection c = (HttpURLConnection) URI.create(u).toURL().openConnection();
        c.setRequestProperty("User-Agent",APP+"/"+VER);
        c.setConnectTimeout(15000);c.setReadTimeout(60000);
        try(InputStream in=c.getInputStream();OutputStream o=Files.newOutputStream(d)){
            byte[]buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)o.write(buf,0,n);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  JSON HELPERS
    // ═══════════════════════════════════════════════════════════════════
    static String osName() {
        String o=System.getProperty("os.name","").toLowerCase();
        return o.contains("win")?"windows":o.contains("mac")?"osx":"linux";
    }
    String js(String j, String k) {
        Matcher m=Pattern.compile("\""+Pattern.quote(k)+"\"\\s*:\\s*\"([^\"]+)\"").matcher(j);
        return m.find()?m.group(1):null;
    }
    String jn(String j, String p, String k, String d) {
        int pi=j.indexOf("\""+p+"\"");if(pi<0)return d;
        int os=j.indexOf('{',pi);if(os<0)return d;
        int dep=0,oe=os;
        for(int i=os;i<j.length();i++){if(j.charAt(i)=='{')dep++;else if(j.charAt(i)=='}'){dep--;if(dep==0){oe=i;break;}}}
        String r=js(j.substring(os,oe+1),k);return r!=null?r:d;
    }
    String jdn(String j, String a, String b, String k) {
        int ai=j.indexOf("\""+a+"\"");if(ai<0)return null;
        int ao=j.indexOf('{',ai);if(ao<0)return null;
        int dep=0,ae=ao;
        for(int i=ao;i<j.length();i++){if(j.charAt(i)=='{')dep++;else if(j.charAt(i)=='}'){dep--;if(dep==0){ae=i;break;}}}
        return jn(j.substring(ao,ae+1),b,k,null);
    }
    String clf(String j, String c, String f) {
        int ci=j.indexOf("\""+c+"\"");if(ci<0)return null;
        int os=j.indexOf('{',ci);if(os<0)return null;
        int dep=0,oe=os;
        for(int i=os;i<j.length();i++){if(j.charAt(i)=='{')dep++;else if(j.charAt(i)=='}'){dep--;if(dep==0){oe=i;break;}}}
        return js(j.substring(os,oe+1),f);
    }
}
