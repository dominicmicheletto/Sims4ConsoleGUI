package main;

import cheats.Cheat;
import gui.*;
import sim.*;
import settings.*;
import gui.autocomplete.*;
import gui.checklistitem.*;
import gui.initialsettings.*;
import gui.menuitem.*;
import gui.sim.*;
import java.awt.event.KeyEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import sim.Sim.Species;

/**
 * ConsoleGUI Java GUI application which provides an interface between the Sims
 * 4's Python and Java using a local host server. This application provides an
 * enhanced graphical user interface for the Game's own Cheats Console, as well
 * as customisable shortcuts, a script editor, and an in-depth cheats explorer.
 *
 * @author Dominic Micheletto
 * @version 1.0.0
 */
public class ConsoleGUI extends javax.swing.JFrame implements CommandStateListener {

    /**
     * The version of ConsoleGUI - Major, Minor, Revision
     */
    private static final int[] VERSION = {
        1, 0, 0
    };

    /**
     * The default port used for the client/server connection
     */
    private static final int DEFAULT_PORT = ('G' << 1) ^ ('U' << 2) ^ ('I' << 3);

    /**
     * A static variable which determines if the Application is in debug mode.
     * When in debug mode, output is sent to Standard Output.
     */
    private static boolean DEBUG_MODE;

    /**
     * The internal hash map which maps the key code with the virtual key it
     * represents. This is populated when jComboBoxSettingsShortcutKey is
     * populated.
     */
    private HashMap<Integer, VirtualKey> virtualKeys;

    private CommandProtocol protocol;

    private ConnectionData connData;

    private ConsoleHistory history;

    /**
     * The settings held by this application which are currently in use.
     */
    private static Settings settings;
    /**
     * The local changes to settings held when customising.
     */
    private Settings currentSettings;

    private java.awt.TrayIcon trayIcon;
    private HashMap<java.awt.Component, Integer> windowMap;

    /**
     * Creates new form ConsoleGUI
     *
     * @param port The port that the local server runs on
     */
    public ConsoleGUI(int port) {
        this.windowMap = new HashMap<>();
        this.initComponents();

        this.createPopupMenu();
        this.nameMenuComponents();
        this.populateMenuItemsComboBox();
        this.populateSystemTrayMenuList();
        this.populateInitialSettingsList();
        this.trayIcon = this.createSystemTrayMenu();
        this.updateTitle();

        this.setUpTreeAndTable();
        try {
            this.currentSettings = (Settings) ConsoleGUI.settings.clone();
        } catch (CloneNotSupportedException ex) {
        }

        ConsoleGUI.settings.colours.keySet().forEach((var category)
                -> this.jComboBoxSettingsEditorColourCategory.addItem(category));
        this.currentSettings.initialSettings.forEach((key, value) ->
            this.initialSettingsHashMap.get(key).setSelected(value));

        this.setUpSize();
        this.implementSettings();

        this.protocol = new CommandProtocol();
        this.setConnected(false);

        try {
            this.waitForConnection(port);
        } catch (IOException ex) {
        }
    }

    private void nameMenuComponents() {
        java.util.Arrays.asList(this.getClass().getDeclaredFields()).stream().filter(
                (var field) -> {
                    try {
                        field.getType().asSubclass(javax.swing.JMenuItem.class);
                        return true;
                    } catch (Exception ex) {
                        return false;
                    }
                }
        ).forEach((var field) -> {
            try {
                var item = (javax.swing.JMenuItem) field.get(this);
                item.setName(field.getName());
            } catch (IllegalArgumentException | IllegalAccessException ex) {
            }
        });
    }

    private void populateMenuItemsComboBox() {
        var count = this.jMenuBarMain.getMenuCount();
        for (int i = 0; i < count; i++) {
            var menu = this.jMenuBarMain.getMenu(i);

            this.jComboBoxSettingsMenuItem.addItem(
                    new MenuItemSeparator(menu.getText()));

            this.getAllSubMenus(menu).forEach((var item)
                    -> this.jComboBoxSettingsMenuItem.addItem(item));
        }
    }

    private ArrayList<MenuItem> getAllSubMenus(javax.swing.JMenuItem item) {
        var list = new ArrayList<MenuItem>();
        if (item == null) {
            return list;
        }

        if (item instanceof javax.swing.JMenu) {
            var menuItem = (javax.swing.JMenu) item;

            if (menuItem.getName().equals("jMenuCurrentWindows")) {
                return list;
            }

            for (int i = 0; i < menuItem.getItemCount(); i++) {
                list.addAll(this.getAllSubMenus(menuItem.getItem(i)));
            }
        } else {
            list.add(new MenuItem(item.getName(), item));
        }

        return list;
    }

    private void populateSystemTrayMenuList() {
        var count = this.jMenuCheats.getMenuComponentCount();
        var model = new CheckListItemModel();
        this.quickCheatsHashMap = new HashMap<>();

        for (var i = 0; i < count; i++) {
            var component = this.jMenuCheats.getMenuComponent(i);

            if (component instanceof javax.swing.JPopupMenu.Separator || component == null) {
                continue;
            }
            var menu = (javax.swing.JMenuItem) component;

            model.addElement(new CheckListItemSeparator(menu.getText()));
            model.addAll(getAllCheats(menu));
        }

        this.jListSystemTrayMenu.setModel(model);
    }
    
    private void populateInitialSettingsList() {
        var count = this.jMenuToggles.getMenuComponentCount();
        var model = new InitialSettingsItemModel();
        this.initialSettingsHashMap = new HashMap<>();
        
        for (var i = 0; i < count; i++) {
            var component = this.jMenuToggles.getMenuComponent(i);
            
            if (component instanceof javax.swing.JPopupMenu.Separator || component == null) {
                continue;
            }
            
            var menu = (javax.swing.JMenuItem) component;
            model.addElement(new InitialSettingsItem(menu));
            this.initialSettingsHashMap.put(menu.getName(), menu);
        }
        
        this.jListInitialSettings.setModel(model);
    }

    private ArrayList<CheckListItem> getAllCheats(javax.swing.JMenuItem item) {
        final var list = new ArrayList<CheckListItem>();
        if (item == null) {
            return list;
        }

        if (item instanceof javax.swing.JMenu) {
            var menuItem = (javax.swing.JMenu) item;

            for (int i = 0; i < menuItem.getItemCount(); i++) {
                this.getAllCheats(menuItem.getItem(i)).forEach((var _item) -> {
                    list.add(_item);
                    this.quickCheatsHashMap.put(_item.item.getName(), _item.item);
                });
            }
        } else {
            list.add(new CheckListItem(item));
            this.quickCheatsHashMap.put(item.getName(), item);
        }

        return list;
    }

    private void setUpSize() {
        this.setSize(ConsoleGUI.settings.defaultDimension);
    }

    private String getRuntime() {
        var version = java.lang.Runtime.version().version();

        return version.stream().map((var value) -> value.toString()).reduce(
                (s1, s2) -> s1.concat(".").concat(s2)).orElse("");
    }

    private javax.swing.table.TableModel getAboutTableModel() {
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        var model = new javax.swing.table.DefaultTableModel(new Object[]{
            bundle.getString("AboutProperty"), bundle.getString("AboutValue")
        }, 0);

        model.addRow(new Object[]{bundle.getString("AboutCreator"), "LeRoiDeTout @ ModTheSims"});
        model.addRow(new Object[]{bundle.getString("AboutVersion"), this.getVersion()});
        model.addRow(new Object[]{bundle.getString("AboutJava"), this.getRuntime()});

        return model;
    }

    private static java.awt.Image createImage(String path, String description) {
        var imageURLStream = ConsoleGUI.class.getResourceAsStream(path);

        if (imageURLStream == null) {
            System.err.println("Resource not found: " + path);
            return null;
        } else {
            try {
                var imageURL = ImageIO.read(imageURLStream);
                return (new javax.swing.ImageIcon(imageURL, description)).getImage();
            }
            catch (IOException ex) {
                return null;
            }
        }
    }

    private HashMap<String, javax.swing.JMenuItem> quickCheatsHashMap;
    private HashMap<String, javax.swing.JMenuItem> initialSettingsHashMap;

    private class SystemTrayQuickCheatsMenu {

        private final java.awt.Menu menu;
        private final java.util.ResourceBundle bundle;

        public SystemTrayQuickCheatsMenu(java.awt.Menu menu) {
            this.menu = menu;
            this.bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        }

        public void update() {
            var cheats = new ArrayList<java.awt.MenuItem>();
            var toggles = new ArrayList<java.awt.MenuItem>();

            this.menu.removeAll();

            ConsoleGUI.settings.quickCheats.stream().map((cheat)
                    -> ConsoleGUI.this.quickCheatsHashMap.get(cheat)).forEachOrdered((theMenu) -> {
                java.awt.MenuItem menuItem;
                final String text = theMenu.getText();
                if (theMenu instanceof javax.swing.JCheckBoxMenuItem) {
                    menuItem = new java.awt.CheckboxMenuItem(text);
                    toggles.add(menuItem);

                    theMenu.addItemListener((var l)
                            -> ((java.awt.CheckboxMenuItem) menuItem).setState(
                                    l.getStateChange() == java.awt.event.ItemEvent.SELECTED));
                    ((java.awt.CheckboxMenuItem) menuItem).addItemListener((var l)
                            -> ((javax.swing.JCheckBoxMenuItem) theMenu).setState(
                                    l.getStateChange() == java.awt.event.ItemEvent.SELECTED));
                } else {
                    menuItem = new java.awt.MenuItem(text);
                    cheats.add(menuItem);
                }

                menuItem.addActionListener((var listener)
                        -> java.util.Arrays.asList(theMenu.getActionListeners()).forEach(
                                (l) -> l.actionPerformed(listener)));
                theMenu.addPropertyChangeListener("enabled", (var listener)
                        -> menuItem.setEnabled((Boolean) listener.getNewValue()));
            });

            if (toggles.isEmpty() && cheats.isEmpty()) {
                var defaultMenuItem = new java.awt.MenuItem(bundle.getString("NoQuickCheatsMenu"));
                defaultMenuItem.setEnabled(false);
                this.menu.add(defaultMenuItem);
            } else {
                toggles.forEach((var item) -> this.menu.add(item));
                if (!toggles.isEmpty() && !cheats.isEmpty()) {
                    this.menu.addSeparator();
                }
                cheats.forEach((var item) -> this.menu.add(item));
            }
        }
    }

    private SystemTrayQuickCheatsMenu quickCheatsMenu;

    private java.awt.TrayIcon createSystemTrayMenu() {
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");

        if (!java.awt.SystemTray.isSupported()) {
            this.jPanelSystemTrayMenu.setEnabled(false);
            this.jLabelSystemTrayMenu.setEnabled(false);
            this.jListSystemTrayMenu.setEnabled(false);

            this.jPanelSystemTrayMenu.setToolTipText(bundle.getString("NoSystemTray"));
            this.jListSystemTrayMenu.setToolTipText(bundle.getString("NoSystemTray"));

            return null;
        }

        try {
            var tray = java.awt.SystemTray.getSystemTray();
            var icon = ConsoleGUI.createImage("../images/Computer3.png", "tray icon")
                    .getScaledInstance(16, 16, java.awt.Image.SCALE_SMOOTH);
        
            var _trayIcon = new java.awt.TrayIcon(icon);

            var popupMenu = new java.awt.PopupMenu();

            popupMenu.add(new java.util.function.Supplier<java.awt.MenuItem>() {
                @Override
                public java.awt.MenuItem get() {
                    var menuItem = new java.awt.MenuItem();

                    menuItem.setLabel(bundle.getString("jMenuItemAbout"));
                    menuItem.addActionListener((var listener) -> {
                        ConsoleGUI.this.requestFocus();
                        java.util.Arrays.asList(
                                ConsoleGUI.this.jMenuItemAbout.getActionListeners())
                                .forEach((var action) -> action.actionPerformed(listener));
                    });

                    return menuItem;
                }
            }.get());
            popupMenu.add(new java.util.function.Supplier<java.awt.MenuItem>() {
                @Override
                public java.awt.MenuItem get() {
                    var menuItem = new java.awt.MenuItem();

                    menuItem.setLabel(bundle.getString("jMenuItemOptions"));
                    menuItem.addActionListener((var listener) -> {
                        ConsoleGUI.this.requestFocus();
                        ConsoleGUI.this.openSettingsWindow();
                    });

                    return menuItem;
                }
            }.get());
            popupMenu.add(new java.util.function.Supplier<java.awt.MenuItem>() {
                @Override
                public java.awt.MenuItem get() {
                    var menu = new java.awt.Menu();

                    menu.setLabel(bundle.getString("jMenuCheats"));
                    ConsoleGUI.this.jMenuCheats.addPropertyChangeListener("enabled", (var listener)
                            -> menu.setEnabled((Boolean) listener.getNewValue()));
                    ConsoleGUI.this.quickCheatsMenu = new SystemTrayQuickCheatsMenu(menu);
                    ConsoleGUI.this.quickCheatsMenu.update();

                    return menu;
                }
            }.get());
            popupMenu.addSeparator();
            popupMenu.add(new java.util.function.Supplier<java.awt.MenuItem>() {
                @Override
                public java.awt.MenuItem get() {
                    var menuItem = new java.awt.MenuItem();

                    menuItem.setLabel(String.format(bundle.getString("TrayMenuItemExit"),
                            ConsoleGUI.this.getTitle()));
                    menuItem.addActionListener((var listener) -> {
                        ConsoleGUI.this.requestFocus();
                        ConsoleGUI.this.closeWindow();
                    });

                    return menuItem;
                }
            }.get());

            _trayIcon.setPopupMenu(popupMenu);

            try {
                tray.add(_trayIcon);
                return _trayIcon;
            } catch (java.awt.AWTException ex) {
                return null;
            }
        }
        catch (NullPointerException ex) {
            this.jPanelSystemTrayMenu.setEnabled(false);
            this.jLabelSystemTrayMenu.setEnabled(false);
            this.jListSystemTrayMenu.setEnabled(false);

            this.jPanelSystemTrayMenu.setToolTipText(bundle.getString("NoSystemTray"));
            this.jListSystemTrayMenu.setToolTipText(bundle.getString("NoSystemTray"));

            return null;
        }
    }

    private HashMap<String, Cheat> cheats;

    private void setUpTreeAndTable() {
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");

        this.cheats = new HashMap<>();
        this.cheats.put("bb.moveobjects",
                new Cheat(
                        "bb.moveobjects",
                        Cheat.Category.BUILD_AND_BUY,
                        Cheat.CheatType.TOGGLE,
                        new ArrayList<>(java.util.Arrays.asList(new Cheat.Parameter[]{
                    new Cheat.Parameter("flag",
                    Cheat.Parameter.ParameterType.BOOLEAN, true,
                    "The flag which determines if Move Objects is enabled or not.")
                })),
                        ""
                ));
        this.cheats.put("headlineeffects",
                new Cheat(
                        "headlineeffects",
                        Cheat.Category.MISCELLANEOUS,
                        Cheat.CheatType.TOGGLE,
                        new ArrayList<>(java.util.Arrays.asList(new Cheat.Parameter[]{
                    new Cheat.Parameter("flag",
                    Cheat.Parameter.ParameterType.BOOLEAN, true,
                    "The flag which determines if Headline Effects are enabled or not.")
                })),
                        ""
                ));

        var categories = new HashMap<Cheat.Category, javax.swing.tree.DefaultMutableTreeNode>();
        var root = new javax.swing.tree.DefaultMutableTreeNode(bundle.getString("jTreeCheatsByTypeRoot"));
        var columnNames = java.util.Arrays.asList(new String[]{
            "Name", "Type", "Params", "Notes"
        }).stream().map(name -> bundle.getString(
                String.format("jTableCheatsByTypeCategoryCheat%s", name))).toArray();
        this.cheats.values().forEach((var cheat) -> {
            if (!categories.containsKey(cheat.getCategory())) {
                var name = bundle.getString(String.format("jTreeCheatsByTypeRoot%s", cheat.getCategory()));
                categories.put(cheat.getCategory(),
                        new javax.swing.tree.DefaultMutableTreeNode(name));
            }

            categories.get(cheat.getCategory()).add(
                    new javax.swing.tree.DefaultMutableTreeNode(cheat));
        });
        categories.values().forEach((category) -> {
            root.add(category);
        });
        this.jTreeCheatsByType.setModel(new javax.swing.tree.DefaultTreeModel(root));
        this.jTableCheatsByType.setModel(new javax.swing.table.DefaultTableModel(
                columnNames, 0));
    }

    private void createPopupMenu() {
        var popupMenu = new javax.swing.JPopupMenu();

        for (var menuItem : this.jMenuView.getMenuComponents()) {
            popupMenu.add(new BoundMenuItem(menuItem));
        }

        this.jDesktopPaneDesktop.setComponentPopupMenu(popupMenu);
    }

    private String getVersion() {
        return String.format("%d.%d.%d", VERSION[0], VERSION[1], VERSION[2]);
    }

    private void updateTitle() {
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");

        this.setTitle(String.format("%s - v%s",
                bundle.getString("ConsoleTitle"), this.getVersion()));
    }

    private static boolean readSettings() {
        return readSettings(new File("Settings.ser"));
    }

    private static boolean readSettings(java.io.File settingsFile) {
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");

        // settings exist
        if (settingsFile.exists()) {
            try (FileInputStream fis = new FileInputStream(settingsFile);
                    ObjectInputStream ois = new ObjectInputStream(fis);) {
                ConsoleGUI.settings = (Settings) ois.readObject();
                return true;
            } catch (InvalidClassException ex) {
                javax.swing.JOptionPane.showMessageDialog(
                        null,
                        bundle.getString("VersionErrorMessage"),
                        bundle.getString("VersionErrorTitle"),
                        javax.swing.JOptionPane.WARNING_MESSAGE
                );
                ConsoleGUI.settings = new Settings();
                return false;
            } catch (IOException | ClassNotFoundException ioe) {
                ConsoleGUI.settings = new Settings();
                return false;
            }
        } // settings do not exist
        else {
            ConsoleGUI.settings = new Settings();

            try (FileOutputStream fos = new FileOutputStream(settingsFile);
                    ObjectOutputStream oos = new ObjectOutputStream(fos);) {
                oos.writeObject(ConsoleGUI.settings);
                oos.close();
                return true;
            } catch (IOException ioe) {
                return false;
            }
        }
    }

    private void saveSettings() {
        this.saveSettings(false);
    }

    private void saveSettings(boolean saveSilently) {
        this.saveSettings(new File("Settings.ser"), saveSilently);
    }

    private void saveSettings(File settingsFile, boolean saveSilently) {
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");

        try (FileOutputStream fos = new FileOutputStream(settingsFile);
                ObjectOutputStream oos = new ObjectOutputStream(fos);) {
            oos.writeObject(this.currentSettings);
            oos.close();

            try {
                ConsoleGUI.settings = (Settings) this.currentSettings.clone();
            } catch (CloneNotSupportedException ex) {
            }
            this.implementSettings();

            if (!saveSilently) {
                var message = bundle.getString("SettingsSavedMessageSuccess");
                var title = bundle.getString("SettingsSavedTitleSuccess");
                javax.swing.JOptionPane.showMessageDialog(this,
                        message, title, javax.swing.JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException ioe) {
            if (saveSilently) {
                return;
            }

            var message = bundle.getString("SettingsSavedMessageFailure");
            var title = bundle.getString("SettingsSavedTitleFailure");
            var options = new String[]{
                bundle.getString("SettingsSavedFailureRetry"),
                bundle.getString("SettingsSavedFailureRevert"),
                bundle.getString("SettingsSavedFailureCancel")
            };
            var choice = javax.swing.JOptionPane.showOptionDialog(
                    this,
                    message,
                    title,
                    javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
                    javax.swing.JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            if (choice == javax.swing.JOptionPane.YES_OPTION) {
                saveSettings();
            } else if (choice == javax.swing.JOptionPane.NO_OPTION) {
                try {
                    ConsoleGUI.settings = (Settings) this.currentSettings.clone();
                } catch (CloneNotSupportedException ex) {
                }
            }
        }

        this.determineIfSettingsChanged();
    }

    private void implementSettings() {
        var font = ConsoleGUI.settings.getFont().deriveFont(java.awt.Font.BOLD);
        this.jTextFieldConsolePrompt.setFont(font);
        this.jLabelConsolePrompt.setFont(font);

        this.updatePreview();

        this.jComboBoxSettingsFontFamily.setSelectedItem(ConsoleGUI.settings.fontName);
        this.jComboBoxSettingsFontSize.setSelectedItem((int) (ConsoleGUI.settings.fontSize));

        var lookAndFeel = ConsoleGUI.settings.lookAndFeel;
        if (lookAndFeel.equals("[System]")) {
            this.jComboBoxSettingsLookAndFeel.setSelectedItem("System");
        } else {
            for (var laf : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if (laf.getName().equals(lookAndFeel)) {
                    this.jComboBoxSettingsLookAndFeel.setSelectedItem(laf.getName());
                    break;
                }
            }
        }

        var colour = ConsoleGUI.settings.colours.get(
                this.jComboBoxSettingsEditorColourCategory.getSelectedItem().toString());
        int red = colour.getRed(), green = colour.getGreen(), blue = colour.getBlue();
        this.jFormattedTextFieldSettingsEditorColour.setText(
                String.format("%02x%02x%02x", red, green, blue));
        this.jLabelSettingsColourPreview.setBackground(colour);

        this.jCheckBoxSettingsOpenAtReady.setSelected(ConsoleGUI.settings.openAtReady);

        var saveDim = ConsoleGUI.settings.savePreviousDimension;
        this.jCheckBoxSettingsSaveWindowSize.setSelected(saveDim);
        this.jTextFieldSettingsWindowHeight.setEnabled(!saveDim);
        this.jTextFieldSettingsWindowWidth.setEnabled(!saveDim);
        var dimension = ConsoleGUI.settings.defaultDimension;
        this.jTextFieldSettingsWindowHeight.setText(String.valueOf(dimension.height));
        this.jTextFieldSettingsWindowWidth.setText(String.valueOf(dimension.width));
        this.setSize(dimension);

        for (int i = 0; i < this.jComboBoxSettingsMenuItem.getItemCount(); i++) {
            var item = this.jComboBoxSettingsMenuItem.getItemAt(i);

            if (item == null || item instanceof MenuItemSeparator) {
                continue;
            }

            var menu = item.item;
            var binding = ConsoleGUI.settings.keyBindings.get(item.name);

            if (binding != null && binding.keyCode != -1) {
                menu.setAccelerator(binding.getKeyStroke());
            } else {
                menu.setAccelerator(null);
            }
        }

        this.jComboBoxSettingsMenuItem.setSelectedIndex(0);

        var sysTrayModel = (CheckListItemModel) this.jListSystemTrayMenu.getModel();
        ConsoleGUI.settings.quickCheats.forEach((cheat) -> {
            sysTrayModel.setState(cheat, true);
        });
        if (this.quickCheatsMenu != null) {
            this.quickCheatsMenu.update();
        }
        
        var initSettingsModel = (InitialSettingsItemModel) this.jListInitialSettings.getModel();
        ConsoleGUI.settings.initialSettings.forEach((key, value) -> {
            initSettingsModel.setState(key, value);
        });
        
        if (ConsoleGUI.settings.mcccSettings != null) {
            this.jCheckBoxMCCC.setSelected(true);
            this.jButtonMCCC.setEnabled(true);
            
            ConsoleGUI.settings.mcccSettings.forEach((key, value) -> {
                var state = initSettingsModel.getState(key) || value;
                ConsoleGUI.this.initialSettingsHashMap.get(key).setSelected(state);
            });
        }
        if (ConsoleGUI.settings.tmexSettings != null) {
            this.jCheckBoxTMEX.setSelected(true);
            this.jButtonTMEX.setEnabled(true);
            
            ConsoleGUI.settings.tmexSettings.forEach((key, value) -> {
                var state = initSettingsModel.getState(key) || value;
                ConsoleGUI.this.initialSettingsHashMap.get(key).setSelected(state);
            });
        }
        
        if (ConsoleGUI.settings.maxTimeout != -1) {
            this.jCheckBoxSettingsUseMaxTimeout.setSelected(true);
            this.jTextFieldMaxTimeOut.setEnabled(true);
            this.jTextFieldMaxTimeOut.setText(String.valueOf(ConsoleGUI.settings.maxTimeout));
        }
        else {
            this.jCheckBoxSettingsUseMaxTimeout.setSelected(false);
            this.jTextFieldMaxTimeOut.setEnabled(false);
            this.jTextFieldMaxTimeOut.setText("");
        }
        
        this.jCheckBoxSettingsUSeNonEssentialQueries.setSelected(ConsoleGUI.settings.useNonEssentialQueries());
    }

    private void createDocumentForConsole() {
        var colours = this.currentSettings.colours;

        var kit = new javax.swing.text.html.HTMLEditorKit();
        var stylesheet = kit.getStyleSheet();
        stylesheet.addRule(String.format("body {font-family: '%s'; font-size: %f;}",
                this.currentSettings.fontName, this.currentSettings.fontSize));
        stylesheet.addRule("p.prompt { font-weight: bold; }");
        stylesheet.addRule(String.format("p.prompt span.command { color: #%s; }",
                ConsoleGUI.getHexStringForColour(colours.get("Prompt"))));
        stylesheet.addRule("blockquote, p { margin-top: 0; margin-bottom: 0; }");
        stylesheet.addRule(String.format("span.success { color: #%s; }",
                ConsoleGUI.getHexStringForColour(colours.get("Output"))));
        stylesheet.addRule(String.format("span.error { color: #%s; }",
                ConsoleGUI.getHexStringForColour(colours.get("Error"))));
        stylesheet.addRule(String.format("span.timedout { color: #%s; }",
                ConsoleGUI.getHexStringForColour(colours.get("Timed Out"))));

        this.jEditorPaneConsoleOutput.setEditorKit(kit);
        this.jEditorPaneConsoleOutput.setDocument(kit.createDefaultDocument());

        this.jEditorPaneSettingsAppearancePreview.setEditorKit(kit);
        this.jEditorPaneSettingsAppearancePreview.setDocument(kit.createDefaultDocument());

        this.jTextFieldConsolePrompt.setFont(this.currentSettings.getFont());
    }

    @Override
    public void commandStateChanged(CommandStateChangedEvent evt) {
        
    }
    
    private void setConnected(boolean connected) {
        final javax.swing.JComponent components[] = {
            this.jCheckBoxMenuItemCheatsConsole,
            this.jToggleButtonCheatsConsole,
            this.jMenuCheats,
            this.jTextFieldConsolePrompt
        };
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");

        for (var component : components) {
            component.setEnabled(
                    this.protocol.currentState.equals(CommandProtocol.CommandState.CONNECTED_READY));
        }

        this.jProgressBarStatus.setVisible(!connected);
        this.jLabelConnectionStatus.setText(bundle.getString(connected
                ? "jLabelConnectionStatusConnected"
                : "jLabelConnectionStatusWaiting"));

        this.jSeparator3.setVisible(connected);
        this.jPanelCommandStatus.setVisible(connected);
        this.jSeparator1.setVisible(!connected);

        if (this.trayIcon != null) {
            this.trayIcon.setToolTip(this.jLabelConnectionStatus.getText());
        }

        this.determineState();
    }
    
    private void determineState() {
        if (this.protocol.currentState == CommandProtocol.CommandState.CONNECTED_NOT_READY)
            this.waitForReady();
    }
    
    private HashMap<String, Sim> simsMap = new HashMap<>();
    private void waitForReady() {
        ((Runnable) new Runnable() {
            @Override
            public void run() {
                var that = ConsoleGUI.this;
                var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
                
                try {
                    var line = that.connData.in.readLine();
                    if (line == null)
                        return;
                    
                    if (line.equals("MESSAGE: Request Close")) {
                        that.closeWindow();
                    }
                    
                    if (line.equals("MESSAGE: Ready")) {
                        try {
                            that.jSeparator1.setVisible(true);
                            that.jProgressBarStatus.setVisible(true);
                            that.jProgressBarStatus.setIndeterminate(false);
                            that.jProgressBarStatus.setValue(0);
                            that.jLabelCommandStatus.setText(
                            bundle.getString("jLabelCommandStatusQuerying"));
                            that.protocol.currentState = CommandProtocol.CommandState.CONNECTED_QUERY_DONE;
                            
                            line = that.connData.in.readLine();
                            if (line != null && line.startsWith("MESSAGE: INFO: SIMS LIST: ")) {
                                var amount = Integer.parseInt(
                                        line.replace("MESSAGE: INFO: SIMS LIST: ", ""));
                                
                                that.jProgressBarStatus.setMaximum(amount);
                                
                                for (int i = 1; i <= amount; i++) {
                                    line = that.connData.in.readLine();
                                    line = line.replace("MESSAGE: INFO: SIM: ", "");
                                    
                                    if (ConsoleGUI.DEBUG_MODE)
                                        System.out.println(i + ": " + line);
                                    
                                    var data = line.split("\0");
                                    var name = data[0];
                                    
                                    that.simsMap.put(name, new Sim(line));
                                    that.jProgressBarStatus.setValue(i);
                                }
                            }
                        }
                        catch (NullPointerException | NumberFormatException ex) {
                        }
                        finally {
                            that.jSeparator1.setVisible(false);
                            that.jProgressBarStatus.setVisible(false);
                            that.jProgressBarStatus.setIndeterminate(true);
                            that.jLabelCommandStatus.setText(
                            bundle.getString("jLabelCommandStatusDone"));
                            that.protocol.currentState = CommandProtocol.CommandState.CONNECTED_QUERY_DONE;
                        }
                        
                        try {
                            line = that.connData.in.readLine();
                            if (Objects.equals(line, "MESSAGE: INFO: MCCC DETECTED: True")) {
                                line = that.connData.in.readLine();
                                line = line.replace("MESSAGE: INFO: ", "");
                                var bitVector = Integer.parseInt(line);
                                
                                ConsoleGUI.settings.mcccSettings = new HashMap<>();
                                int i = 0;
                                for (var key : new String[]{
                                    "jCheckBoxMenuItemMoveObjects",
                                    "jCheckBoxMenuItemHiddenObjects",
                                    "jCheckBoxMenuItemTestingCheats",
                                    "jCheckBoxMenuItemGamePlayUnlocks",
                                    "jCheckBoxMenuItemHoverEffects",
                                    "jCheckBoxMenuItemHeadlineEffects",
                                    "jCheckBoxMenuItemFullEditMode"}) {
                                    ConsoleGUI.settings.mcccSettings.put(key, 
                                            (bitVector & (1 << i)) >> i == 1);
                                    i++;
                                }
                            }
                        } catch (NullPointerException | NumberFormatException ex) {
                        }
                        
                        try {
                            line = that.connData.in.readLine();
                            if (Objects.equals(line, "MESSAGE: INFO: TMEX DETECTED: True")) {
                                line = that.connData.in.readLine();
                                line = line.replace("MESSAGE: INFO: ", "");
                                var bitVector = Integer.parseInt(line);
                                
                                ConsoleGUI.settings.tmexSettings = new HashMap<>();
                                int i = 0;
                                for (var key : new String[]{
                                    "jCheckBoxMenuItemMoveObjects",
                                    "jCheckBoxMenuItemTestingCheats",
                                    "jCheckBoxMenuItemFullEditMode"
                                }) {
                                    ConsoleGUI.settings.tmexSettings.put(key,
                                            (bitVector & (1 << i)) >> i == 1);
                                    i++;
                                }
                            }
                        }
                        catch (NullPointerException | NumberFormatException ex) {
                        }
                        
                        that.implementSettings();
                        
                        that.protocol.currentState =
                                CommandProtocol.CommandState.CONNECTED_READY;
                        that.setConnected(true);
                        
                        if (that.currentSettings.openAtReady) {
                            that.jCheckBoxMenuItemCheatsConsole.doClick();
                        }
                    }
                    
                } catch (IOException ex) {
                }
            }
        }).run();
    }

    private void waitForConnection(int port) throws IOException {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            private Socket echoSocket, clientSocket;
            private ServerSocket server;
            private final java.util.ResourceBundle bundle;

            {
                this.bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
            }

            private boolean testConnection() {
                if (this.echoSocket == null) {
                    return false;
                }
                try {
                    var line = new BufferedReader(
                            new InputStreamReader(this.echoSocket.getInputStream())).readLine();

                    this.clientSocket = this.server.accept();
                    var in = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));

                    ConsoleGUI.this.connData = new ConnectionData(
                            port, this.server, this.clientSocket, this.echoSocket,
                            new PrintWriter(this.clientSocket.getOutputStream()), in
                    );
                    
                    System.out.println(line);
                    ConsoleGUI.this.protocol.currentState = CommandProtocol.CommandState.CONNECTED_NOT_READY;

                    return line.equals("MESSAGE: Connect");
                } catch (IOException ex) {
                    this.echoSocket = null;
                    ConsoleGUI.this.jLabelConnectionStatus.setText(
                            this.bundle.getString("jLabelConnectionStatusDisconnected"));
                    ConsoleGUI.this.protocol.currentState = CommandProtocol.CommandState.DISCONNECTED;
                    return false;
                }
            }

            @Override
            public void run() {
                if (this.echoSocket == null || this.clientSocket == null) {
                    try (var _server = new ServerSocket(port, 2);) {
                        this.server = _server;
                        _server.setSoTimeout(2000);
                        this.echoSocket = _server.accept();
                        ConsoleGUI.this.setConnected(this.testConnection());
                    } catch (IOException ex) {
                        ConsoleGUI.this.setConnected(false);
                        if (ConsoleGUI.DEBUG_MODE) {
                            System.out.println("Failed to connect");
                        }
                    }
                } else {
                    ConsoleGUI.this.setConnected(this.testConnection());
                }
            }

        }, 0, 1000);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jDialogAbout = new javax.swing.JDialog();
        jPanelAbout = new javax.swing.JPanel();
        jSplitPane2 = new javax.swing.JSplitPane();
        jScrollPane5 = new javax.swing.JScrollPane();
        jTableAbout = new javax.swing.JTable();
        jScrollPane7 = new javax.swing.JScrollPane();
        jEditorPaneAbout = new javax.swing.JEditorPane();
        jButtonAboutClose = new javax.swing.JButton();
        jDialogMCCCSettings = new javax.swing.JDialog(this);
        jScrollPane9 = new javax.swing.JScrollPane();
        jTableMCCCSettings = new javax.swing.JTable();
        jButtonMCCCSettingsOk = new javax.swing.JButton();
        jDialogTMexSettings = new javax.swing.JDialog(this);
        jScrollPane10 = new javax.swing.JScrollPane();
        jTableTMexSettings = new javax.swing.JTable();
        jButtonTMexSettingsOk = new javax.swing.JButton();
        jDialogMoneyChooser = new javax.swing.JDialog();
        jPanelMoneyChooser = new javax.swing.JPanel();
        jLabelMoneyChooserAmount = new javax.swing.JLabel();
        jSpinnerMoneyChooser = new javax.swing.JSpinner();
        jPanelMoneyChooserButtons = new javax.swing.JPanel();
        jButtonMoneyChooserOk = new javax.swing.JButton();
        jButtonMoneyChooserCancel = new javax.swing.JButton();
        jLabelMoneyChooserDescription = new javax.swing.JLabel();
        jDialogEditRelationship = new javax.swing.JDialog();
        jPanelRelationshipSimChooser = new javax.swing.JPanel();
        jLabelRelationshipMainSim = new javax.swing.JLabel();
        jComboBoxRelationshipMainSim = new javax.swing.JComboBox<>();
        jLabelRelationshipSecondarySim = new javax.swing.JLabel();
        jComboBoxRelationshipSecondarySim = new javax.swing.JComboBox<>();
        jPanelRelationshipRelationshipType = new javax.swing.JPanel();
        jRadioButtonRelationshipFriendly = new javax.swing.JRadioButton();
        jRadioButtonRelationshipRomantic = new javax.swing.JRadioButton();
        jPanelRelationshipRelationshipValue = new javax.swing.JPanel();
        jSliderRelationshipRelationshipValue = new javax.swing.JSlider();
        jPanelRelationshipButtons = new javax.swing.JPanel();
        jButtonRelationshipOk = new javax.swing.JButton();
        jButtonRelationshipCancel = new javax.swing.JButton();
        buttonGroupRelationship = new javax.swing.ButtonGroup();
        jToolBarCheatsBar = new javax.swing.JToolBar();
        jToggleButtonCheatsConsole = new javax.swing.JToggleButton();
        jToggleButtonCheatsExplorer = new javax.swing.JToggleButton();
        jToolBarStatusBar = new javax.swing.JToolBar();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 32767));
        jLabelConnectionStatus = new javax.swing.JLabel();
        filler6 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 32767));
        jSeparator2 = new javax.swing.JToolBar.Separator();
        filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 32767));
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 10), new java.awt.Dimension(0, 10), new java.awt.Dimension(32767, 10));
        jSeparator3 = new javax.swing.JToolBar.Separator();
        jPanelCommandStatus = new javax.swing.JPanel();
        filler9 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 32767));
        filler10 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 32767));
        jLabelCommandStatus = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 32767));
        jProgressBarStatus = new javax.swing.JProgressBar();
        filler4 = new javax.swing.Box.Filler(new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 0), new java.awt.Dimension(10, 32767));
        jDesktopPaneDesktop = new javax.swing.JDesktopPane();
        jInternalFrameConsole = new javax.swing.JInternalFrame();
        jPanelConsole = new javax.swing.JPanel();
        jLabelConsolePrompt = new javax.swing.JLabel();
        jTextFieldConsolePrompt = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        jEditorPaneConsoleOutput = new javax.swing.JEditorPane();
        jInternalFrameCheatsExplorer = new javax.swing.JInternalFrame();
        jSplitPane1 = new javax.swing.JSplitPane();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTreeCheatsByType = new javax.swing.JTree();
        jScrollPane4 = new javax.swing.JScrollPane();
        jTableCheatsByType = new javax.swing.JTable();
        jInternalFrameSettings = new javax.swing.JInternalFrame();
        jTabbedPaneSettings = new javax.swing.JTabbedPane();
        jPanelSettingsAppearance = new javax.swing.JPanel();
        jPanelSettingsConsoleGUI = new javax.swing.JPanel();
        jLabelSettingsLookAndFeel = new javax.swing.JLabel();
        jComboBoxSettingsLookAndFeel = new javax.swing.JComboBox<>();
        jPanelSettingsCheatsConsole = new javax.swing.JPanel();
        jLabelSettingsFontFamily = new javax.swing.JLabel();
        jComboBoxSettingsFontFamily = new javax.swing.JComboBox<>();
        jComboBoxSettingsFontSize = new javax.swing.JComboBox<>();
        jLabelSettingsFontSize = new javax.swing.JLabel();
        jPanelSettingsEditorColour = new javax.swing.JPanel();
        jLabelSettingsEditorColourCategory = new javax.swing.JLabel();
        jComboBoxSettingsEditorColourCategory = new javax.swing.JComboBox<>();
        jLabelSettingsEditorColour = new javax.swing.JLabel();
        jFormattedTextFieldSettingsEditorColour = new javax.swing.JFormattedTextField();
        jLabelColourHexHash = new javax.swing.JLabel();
        jButtonSettingsChooseColour = new javax.swing.JButton();
        jLabelSettingsColourPreview = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        jEditorPaneSettingsAppearancePreview = new javax.swing.JEditorPane();
        jLabelSettingsPreview = new javax.swing.JLabel();
        jPanelSettingsKeyBindings = new javax.swing.JPanel();
        jPanelSettingsMenuBindings = new javax.swing.JPanel();
        jLabelSettingsMenuItem = new javax.swing.JLabel();
        jComboBoxSettingsMenuItem = new javax.swing.JComboBox<>();
        jPanelSettingsMenuItemShortcut = new javax.swing.JPanel();
        jLabelSettingsKey = new javax.swing.JLabel();
        jComboBoxSettingsShortcutKey = new javax.swing.JComboBox<>();
        jPanelSettingsShortcutControlKeys = new javax.swing.JPanel();
        jCheckBoxSettingsCtrl = new javax.swing.JCheckBox();
        jCheckBoxSettingsShift = new javax.swing.JCheckBox();
        jCheckBoxSettingsAlt = new javax.swing.JCheckBox();
        jLabelSettingsMenuItemKeyStroke = new javax.swing.JLabel();
        jTextFieldKeyStroke = new javax.swing.JTextField();
        jPanelSystemTrayMenu = new javax.swing.JPanel();
        jLabelSystemTrayMenu = new javax.swing.JLabel();
        jScrollPane6 = new javax.swing.JScrollPane();
        jListSystemTrayMenu = new javax.swing.JList<>();
        jPanelToolbar = new javax.swing.JPanel();
        jPanelSettingsEditor = new javax.swing.JPanel();
        jPanelSettingsMiscellaneous = new javax.swing.JPanel();
        jPanelSettingsGeneral = new javax.swing.JPanel();
        jCheckBoxSettingsOpenAtReady = new javax.swing.JCheckBox();
        jPanelSettingsTimeOut = new javax.swing.JPanel();
        jCheckBoxSettingsUseMaxTimeout = new javax.swing.JCheckBox();
        jTextFieldMaxTimeOut = new javax.swing.JTextField();
        jSeparator16 = new javax.swing.JSeparator();
        jCheckBoxSettingsUSeNonEssentialQueries = new javax.swing.JCheckBox();
        jPanelSettingsSizing = new javax.swing.JPanel();
        jCheckBoxSettingsSaveWindowSize = new javax.swing.JCheckBox();
        jLabelSettingsWindowHeight = new javax.swing.JLabel();
        jTextFieldSettingsWindowHeight = new javax.swing.JTextField();
        jLabelSettingsWindowWidth = new javax.swing.JLabel();
        jTextFieldSettingsWindowWidth = new javax.swing.JTextField();
        jPanelInitialSettings = new javax.swing.JPanel();
        jScrollPane8 = new javax.swing.JScrollPane();
        jListInitialSettings = new javax.swing.JList<>();
        jPanelMCCC = new javax.swing.JPanel();
        jCheckBoxMCCC = new javax.swing.JCheckBox();
        jButtonMCCC = new javax.swing.JButton();
        jPanelTMEX = new javax.swing.JPanel();
        jCheckBoxTMEX = new javax.swing.JCheckBox();
        jButtonTMEX = new javax.swing.JButton();
        jPanelSettingsButtons = new javax.swing.JPanel();
        jPanelSettingsImportExport = new javax.swing.JPanel();
        jButtonSettingsExport = new javax.swing.JButton();
        jButtonSettingsImport = new javax.swing.JButton();
        jPanelSettingsOkApplyCancel = new javax.swing.JPanel();
        jButtonSettingsOK = new javax.swing.JButton();
        jButtonSettingsApply = new javax.swing.JButton();
        jButtonSettingsCancel = new javax.swing.JButton();
        jMenuBarMain = new javax.swing.JMenuBar();
        jMenuFile = new javax.swing.JMenu();
        jMenuItemOptions = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JPopupMenu.Separator();
        jMenuItemExit = new javax.swing.JMenuItem();
        jMenuView = new javax.swing.JMenu();
        jCheckBoxMenuItemCheatsConsole = new javax.swing.JCheckBoxMenuItem();
        jCheckBoxMenuItemCheatsExplorer = new javax.swing.JCheckBoxMenuItem();
        jMenuCheats = new javax.swing.JMenu();
        jMenuToggles = new javax.swing.JMenu();
        jCheckBoxMenuItemTestingCheats = new javax.swing.JCheckBoxMenuItem();
        jSeparator7 = new javax.swing.JPopupMenu.Separator();
        jCheckBoxMenuItemHoverEffects = new javax.swing.JCheckBoxMenuItem();
        jCheckBoxMenuItemHeadlineEffects = new javax.swing.JCheckBoxMenuItem();
        jSeparator8 = new javax.swing.JPopupMenu.Separator();
        jCheckBoxMenuItemMoveObjects = new javax.swing.JCheckBoxMenuItem();
        jCheckBoxMenuItemHiddenObjects = new javax.swing.JCheckBoxMenuItem();
        jCheckBoxMenuItemGamePlayUnlocks = new javax.swing.JCheckBoxMenuItem();
        jCheckBoxMenuItemFreeRealEstate = new javax.swing.JCheckBoxMenuItem();
        jSeparator9 = new javax.swing.JPopupMenu.Separator();
        jCheckBoxMenuItemFullEditMode = new javax.swing.JCheckBoxMenuItem();
        jSeparator10 = new javax.swing.JPopupMenu.Separator();
        jMenuMoney = new javax.swing.JMenu();
        jMenuItemMotherlode = new javax.swing.JMenuItem();
        jMenuItemRosebud = new javax.swing.JMenuItem();
        jSeparator14 = new javax.swing.JPopupMenu.Separator();
        jMenuItemClearMoney = new javax.swing.JMenuItem();
        jMenuItemSetMoney = new javax.swing.JMenuItem();
        jSeparator15 = new javax.swing.JPopupMenu.Separator();
        jMenuItemDepositMoney = new javax.swing.JMenuItem();
        jMenuItemWithdrawMoney = new javax.swing.JMenuItem();
        jSeparator12 = new javax.swing.JPopupMenu.Separator();
        jMenuSkillCheats = new javax.swing.JMenu();
        jMenuItemSetSkill = new javax.swing.JMenuItem();
        jMenuItemClearAllSkills = new javax.swing.JMenuItem();
        jMenuCareerCheats = new javax.swing.JMenu();
        jMenuItemPromote = new javax.swing.JMenuItem();
        jMenuItemDemote = new javax.swing.JMenuItem();
        jSeparator11 = new javax.swing.JPopupMenu.Separator();
        jMenuBuildBuy = new javax.swing.JMenu();
        jMenuItemEnableFreeBuild = new javax.swing.JMenuItem();
        jSeparator13 = new javax.swing.JPopupMenu.Separator();
        jMenuRelationshipCheats = new javax.swing.JMenu();
        jMenuItemEditRelationship = new javax.swing.JMenuItem();
        jMenuItemClearAllRelationships = new javax.swing.JMenuItem();
        jMenuItemIntroduceSelfToEveryone = new javax.swing.JMenuItem();
        jMenuWindow = new javax.swing.JMenu();
        jMenuItemCascade = new javax.swing.JMenuItem();
        jSeparator5 = new javax.swing.JPopupMenu.Separator();
        jMenuCurrentWindows = new javax.swing.JMenu();
        jMenuItemNoWindows = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JPopupMenu.Separator();
        jMenuItemClose = new javax.swing.JMenuItem();
        jMenuItemCloseAll = new javax.swing.JMenuItem();
        jMenuHelp = new javax.swing.JMenu();
        jMenuItemAbout = new javax.swing.JMenuItem();

        jDialogAbout.setTitle(java.text.MessageFormat.format(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jDialogAbout"), new Object[] {java.util.ResourceBundle.getBundle("ConsoleGUI").getString("ConsoleTitle")})); // NOI18N
        jDialogAbout.setAlwaysOnTop(true);
        jDialogAbout.setMinimumSize(new java.awt.Dimension(450, 550));
        jDialogAbout.setModal(true);
        jDialogAbout.setName("About"); // NOI18N
        jDialogAbout.setResizable(false);

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("main/ConsoleGUI"); // NOI18N
        jPanelAbout.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("jPanelAbout"))); // NOI18N

        jSplitPane2.setDividerLocation(250);
        jSplitPane2.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        jTableAbout.setModel(this.getAboutTableModel());
        jTableAbout.setEnabled(false);
        jScrollPane5.setViewportView(jTableAbout);

        jSplitPane2.setBottomComponent(jScrollPane5);

        jEditorPaneAbout.setEditable(false);
        jScrollPane7.setViewportView(jEditorPaneAbout);
        var aboutText = java.util.ResourceBundle.getBundle("ConsoleGUI").getString("jEditorPaneAbout");
        var kit = new javax.swing.text.html.HTMLEditorKit();
        jEditorPaneAbout.setEditorKit(kit);
        jEditorPaneAbout.setDocument(kit.createDefaultDocument());

        var aboutTextBuilder = new StringBuilder();
        aboutTextBuilder.append("<html><body>");
        for (var line : aboutText.split("\n\n")) {
            aboutTextBuilder.append("<div>");
            for (var paragraph : line.split("\n")) {
                aboutTextBuilder.append("<p>");
                aboutTextBuilder.append(paragraph);
                aboutTextBuilder.append("</p>");
            }
            aboutTextBuilder.append("</div>");
        }
        aboutTextBuilder.append("</body></html>");
        jEditorPaneAbout.setText(aboutTextBuilder.toString());

        jSplitPane2.setLeftComponent(jScrollPane7);

        javax.swing.GroupLayout jPanelAboutLayout = new javax.swing.GroupLayout(jPanelAbout);
        jPanelAbout.setLayout(jPanelAboutLayout);
        jPanelAboutLayout.setHorizontalGroup(
            jPanelAboutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelAboutLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSplitPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 362, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanelAboutLayout.setVerticalGroup(
            jPanelAboutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelAboutLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSplitPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 277, Short.MAX_VALUE)
                .addContainerGap())
        );

        jButtonAboutClose.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jButtonAboutCloseMnemonic").charAt(0));
        jButtonAboutClose.setText(bundle.getString("jButtonAboutClose")); // NOI18N
        jButtonAboutClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonAboutCloseActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jDialogAboutLayout = new javax.swing.GroupLayout(jDialogAbout.getContentPane());
        jDialogAbout.getContentPane().setLayout(jDialogAboutLayout);
        jDialogAboutLayout.setHorizontalGroup(
            jDialogAboutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialogAboutLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jDialogAboutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelAbout, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jDialogAboutLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jButtonAboutClose, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jDialogAboutLayout.setVerticalGroup(
            jDialogAboutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialogAboutLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelAbout, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButtonAboutClose)
                .addContainerGap())
        );

        jDialogAbout.getAccessibleContext().setAccessibleName("About");

        jDialogMCCCSettings.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        java.util.ResourceBundle bundle1 = java.util.ResourceBundle.getBundle("ConsoleGUI"); // NOI18N
        jDialogMCCCSettings.setTitle(bundle1.getString("jDialogMCCCSettings")); // NOI18N
        jDialogMCCCSettings.setAlwaysOnTop(true);
        jDialogMCCCSettings.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        jDialogMCCCSettings.setName("MCCC Settings"); // NOI18N
        jDialogMCCCSettings.setResizable(false);

        jTableMCCCSettings.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        jTableMCCCSettings.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        jScrollPane9.setViewportView(jTableMCCCSettings);

        jButtonMCCCSettingsOk.setText(bundle1.getString("jButtonMCCCSettingsOk")); // NOI18N
        jButtonMCCCSettingsOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonMCCCSettingsOkActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jDialogMCCCSettingsLayout = new javax.swing.GroupLayout(jDialogMCCCSettings.getContentPane());
        jDialogMCCCSettings.getContentPane().setLayout(jDialogMCCCSettingsLayout);
        jDialogMCCCSettingsLayout.setHorizontalGroup(
            jDialogMCCCSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialogMCCCSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jDialogMCCCSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jButtonMCCCSettingsOk, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jScrollPane9, javax.swing.GroupLayout.PREFERRED_SIZE, 375, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(15, Short.MAX_VALUE))
        );
        jDialogMCCCSettingsLayout.setVerticalGroup(
            jDialogMCCCSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialogMCCCSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane9, javax.swing.GroupLayout.DEFAULT_SIZE, 253, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonMCCCSettingsOk)
                .addContainerGap())
        );

        jDialogTMexSettings.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        jDialogTMexSettings.setTitle(bundle1.getString("jDialogTMexSettings")); // NOI18N
        jDialogTMexSettings.setAlwaysOnTop(true);
        jDialogTMexSettings.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        jDialogTMexSettings.setName("TMex Settings"); // NOI18N
        jDialogTMexSettings.setResizable(false);

        jTableTMexSettings.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        jTableTMexSettings.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        jScrollPane10.setViewportView(jTableTMexSettings);

        jButtonTMexSettingsOk.setText(bundle1.getString("jButtonMCCCSettingsOk")); // NOI18N
        jButtonTMexSettingsOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonTMexSettingsOkActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jDialogTMexSettingsLayout = new javax.swing.GroupLayout(jDialogTMexSettings.getContentPane());
        jDialogTMexSettings.getContentPane().setLayout(jDialogTMexSettingsLayout);
        jDialogTMexSettingsLayout.setHorizontalGroup(
            jDialogTMexSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialogTMexSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jDialogTMexSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jButtonTMexSettingsOk, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jScrollPane10, javax.swing.GroupLayout.DEFAULT_SIZE, 375, Short.MAX_VALUE))
                .addGap(15, 15, 15))
        );
        jDialogTMexSettingsLayout.setVerticalGroup(
            jDialogTMexSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialogTMexSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane10, javax.swing.GroupLayout.DEFAULT_SIZE, 253, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonTMexSettingsOk)
                .addContainerGap())
        );

        jDialogMoneyChooser.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        jDialogMoneyChooser.setAlwaysOnTop(true);
        jDialogMoneyChooser.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        jDialogMoneyChooser.setName("Money Chooser"); // NOI18N

        jPanelMoneyChooser.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        jLabelMoneyChooserAmount.setText(bundle1.getString("jLabelMoneyChooserAmount")); // NOI18N

        jSpinnerMoneyChooser.setModel(new javax.swing.SpinnerNumberModel(0, 0, 9999999, 1));

        javax.swing.GroupLayout jPanelMoneyChooserLayout = new javax.swing.GroupLayout(jPanelMoneyChooser);
        jPanelMoneyChooser.setLayout(jPanelMoneyChooserLayout);
        jPanelMoneyChooserLayout.setHorizontalGroup(
            jPanelMoneyChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelMoneyChooserLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabelMoneyChooserAmount, javax.swing.GroupLayout.DEFAULT_SIZE, 48, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSpinnerMoneyChooser, javax.swing.GroupLayout.PREFERRED_SIZE, 304, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanelMoneyChooserLayout.setVerticalGroup(
            jPanelMoneyChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelMoneyChooserLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelMoneyChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelMoneyChooserAmount)
                    .addComponent(jSpinnerMoneyChooser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        var comp = jSpinnerMoneyChooser.getEditor();
        var spinnerField = (javax.swing.JFormattedTextField) comp.getComponent(0);
        var formatter = (javax.swing.text.DefaultFormatter) spinnerField.getFormatter();
        formatter.setCommitsOnValidEdit(true);
        formatter.setAllowsInvalid(false);
        spinnerField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent evt) {
                if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    ConsoleGUI.this.jButtonMoneyChooserOk.requestFocus();
                }
            }
        });

        jButtonMoneyChooserOk.setText(bundle1.getString("jButtonMoneyChooserOk")); // NOI18N
        jButtonMoneyChooserOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonMoneyChooserOkActionPerformed(evt);
            }
        });

        jButtonMoneyChooserCancel.setText(bundle1.getString("jButtonMoneyChooserCancel")); // NOI18N
        jButtonMoneyChooserCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonMoneyChooserCancelActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelMoneyChooserButtonsLayout = new javax.swing.GroupLayout(jPanelMoneyChooserButtons);
        jPanelMoneyChooserButtons.setLayout(jPanelMoneyChooserButtonsLayout);
        jPanelMoneyChooserButtonsLayout.setHorizontalGroup(
            jPanelMoneyChooserButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelMoneyChooserButtonsLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButtonMoneyChooserOk, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonMoneyChooserCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanelMoneyChooserButtonsLayout.setVerticalGroup(
            jPanelMoneyChooserButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelMoneyChooserButtonsLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanelMoneyChooserButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonMoneyChooserCancel)
                    .addComponent(jButtonMoneyChooserOk))
                .addContainerGap())
        );

        javax.swing.GroupLayout jDialogMoneyChooserLayout = new javax.swing.GroupLayout(jDialogMoneyChooser.getContentPane());
        jDialogMoneyChooser.getContentPane().setLayout(jDialogMoneyChooserLayout);
        jDialogMoneyChooserLayout.setHorizontalGroup(
            jDialogMoneyChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialogMoneyChooserLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jDialogMoneyChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelMoneyChooser, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelMoneyChooserButtons, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabelMoneyChooserDescription, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jDialogMoneyChooserLayout.setVerticalGroup(
            jDialogMoneyChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialogMoneyChooserLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabelMoneyChooserDescription, javax.swing.GroupLayout.DEFAULT_SIZE, 21, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanelMoneyChooser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanelMoneyChooserButtons, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jDialogEditRelationship.setTitle(bundle1.getString("jDialogEditRelationship")); // NOI18N
        jDialogEditRelationship.setAlwaysOnTop(true);
        jDialogEditRelationship.setModal(true);
        jDialogEditRelationship.setModalExclusionType(java.awt.Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        jDialogEditRelationship.setName("Edit Relationship"); // NOI18N
        jDialogEditRelationship.setResizable(false);

        jPanelRelationshipSimChooser.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle1.getString("jPanelRelationshipSimChooser"))); // NOI18N

        jLabelRelationshipMainSim.setLabelFor(jComboBoxRelationshipMainSim);
        jLabelRelationshipMainSim.setText(bundle1.getString("jLabelRelationshipMainSim")); // NOI18N

        jComboBoxRelationshipMainSim.setRenderer(new SimItemListCellRenderer());

        jLabelRelationshipSecondarySim.setLabelFor(jComboBoxRelationshipSecondarySim);
        jLabelRelationshipSecondarySim.setText(bundle1.getString("jLabelRelationshipSecondarySim")); // NOI18N

        jComboBoxRelationshipSecondarySim.setRenderer(new SimItemListCellRenderer());

        javax.swing.GroupLayout jPanelRelationshipSimChooserLayout = new javax.swing.GroupLayout(jPanelRelationshipSimChooser);
        jPanelRelationshipSimChooser.setLayout(jPanelRelationshipSimChooserLayout);
        jPanelRelationshipSimChooserLayout.setHorizontalGroup(
            jPanelRelationshipSimChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelRelationshipSimChooserLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelRelationshipSimChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabelRelationshipMainSim, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabelRelationshipSecondarySim, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelRelationshipSimChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jComboBoxRelationshipSecondarySim, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jComboBoxRelationshipMainSim, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelRelationshipSimChooserLayout.setVerticalGroup(
            jPanelRelationshipSimChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelRelationshipSimChooserLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelRelationshipSimChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelRelationshipMainSim)
                    .addComponent(jComboBoxRelationshipMainSim, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelRelationshipSimChooserLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelRelationshipSecondarySim)
                    .addComponent(jComboBoxRelationshipSecondarySim, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jComboBoxRelationshipMainSim.addActionListener(new java.awt.event.ActionListener() {
            private SimItem currentItem;
            private javax.swing.JComboBox<SimItem> parent;

            {
                this.parent = ConsoleGUI.this.jComboBoxRelationshipMainSim;
                this.currentItem = null;
            }

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                var selectedItem = this.parent.getSelectedItem();
                if (selectedItem == null)
                return;

                if (selectedItem instanceof SimItemSeparator) {
                    this.parent.setSelectedItem(this.currentItem);
                }
                else {
                    this.currentItem = (SimItem) selectedItem;
                    jComboBoxRelationshipMainSimActionPerformed(evt);
                }
            }
        });
        jComboBoxRelationshipSecondarySim.addActionListener(new java.awt.event.ActionListener() {
            private SimItem currentItem;
            private javax.swing.JComboBox<SimItem> parent;

            {
                this.parent = ConsoleGUI.this.jComboBoxRelationshipSecondarySim;
                this.currentItem = null;
            }

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                var selectedItem = this.parent.getSelectedItem();
                if (selectedItem == null)
                return;

                if (selectedItem instanceof SimItemSeparator) {
                    this.parent.setSelectedItem(this.currentItem);
                }
                else {
                    this.currentItem = (SimItem) selectedItem;
                    jComboBoxRelationshipSecondarySimActionPerformed(evt);
                }
            }
        });

        jPanelRelationshipRelationshipType.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle1.getString("jPanelRelationshipRelationshipType"))); // NOI18N

        buttonGroupRelationship.add(jRadioButtonRelationshipFriendly);
        jRadioButtonRelationshipFriendly.setSelected(true);
        jRadioButtonRelationshipFriendly.setText(bundle1.getString("jRadioButtonRelationshipFriendly")); // NOI18N
        jRadioButtonRelationshipFriendly.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonRelationshipFriendlyActionPerformed(evt);
            }
        });
        jPanelRelationshipRelationshipType.add(jRadioButtonRelationshipFriendly);

        buttonGroupRelationship.add(jRadioButtonRelationshipRomantic);
        jRadioButtonRelationshipRomantic.setText(bundle1.getString("jRadioButtonRelationshipRomantic")); // NOI18N
        jRadioButtonRelationshipRomantic.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jRadioButtonRelationshipRomanticActionPerformed(evt);
            }
        });
        jPanelRelationshipRelationshipType.add(jRadioButtonRelationshipRomantic);

        jPanelRelationshipRelationshipValue.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle1.getString("jPanelRelationshipRelationshipValue"))); // NOI18N

        jSliderRelationshipRelationshipValue.setMajorTickSpacing(10);
        jSliderRelationshipRelationshipValue.setMinimum(-100);
        jSliderRelationshipRelationshipValue.setPaintTicks(true);
        jSliderRelationshipRelationshipValue.setValue(0);

        javax.swing.GroupLayout jPanelRelationshipRelationshipValueLayout = new javax.swing.GroupLayout(jPanelRelationshipRelationshipValue);
        jPanelRelationshipRelationshipValue.setLayout(jPanelRelationshipRelationshipValueLayout);
        jPanelRelationshipRelationshipValueLayout.setHorizontalGroup(
            jPanelRelationshipRelationshipValueLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelRelationshipRelationshipValueLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSliderRelationshipRelationshipValue, javax.swing.GroupLayout.DEFAULT_SIZE, 356, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanelRelationshipRelationshipValueLayout.setVerticalGroup(
            jPanelRelationshipRelationshipValueLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelRelationshipRelationshipValueLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSliderRelationshipRelationshipValue, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jButtonRelationshipOk.setMnemonic(java.util.ResourceBundle.getBundle("ConsoleGUI").getString("jButtonRelationshipOkMnemonic").charAt(0));
        jButtonRelationshipOk.setText(bundle1.getString("jButtonRelationshipOk")); // NOI18N
        jButtonRelationshipOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonRelationshipOkActionPerformed(evt);
            }
        });

        jButtonRelationshipCancel.setMnemonic(java.util.ResourceBundle.getBundle("ConsoleGUI").getString("jButtonRelationshipCancelMnemonic").charAt(0));
        jButtonRelationshipCancel.setText(bundle1.getString("jButtonRelationshipCancel")); // NOI18N
        jButtonRelationshipCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonRelationshipCancelActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelRelationshipButtonsLayout = new javax.swing.GroupLayout(jPanelRelationshipButtons);
        jPanelRelationshipButtons.setLayout(jPanelRelationshipButtonsLayout);
        jPanelRelationshipButtonsLayout.setHorizontalGroup(
            jPanelRelationshipButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelRelationshipButtonsLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jButtonRelationshipOk, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButtonRelationshipCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanelRelationshipButtonsLayout.setVerticalGroup(
            jPanelRelationshipButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelRelationshipButtonsLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(jPanelRelationshipButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButtonRelationshipOk)
                    .addComponent(jButtonRelationshipCancel))
                .addContainerGap())
        );

        javax.swing.GroupLayout jDialogEditRelationshipLayout = new javax.swing.GroupLayout(jDialogEditRelationship.getContentPane());
        jDialogEditRelationship.getContentPane().setLayout(jDialogEditRelationshipLayout);
        jDialogEditRelationshipLayout.setHorizontalGroup(
            jDialogEditRelationshipLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialogEditRelationshipLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jDialogEditRelationshipLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelRelationshipButtons, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jDialogEditRelationshipLayout.createSequentialGroup()
                        .addGroup(jDialogEditRelationshipLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jPanelRelationshipRelationshipValue, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanelRelationshipSimChooser, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanelRelationshipRelationshipType, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addContainerGap())))
        );
        jDialogEditRelationshipLayout.setVerticalGroup(
            jDialogEditRelationshipLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jDialogEditRelationshipLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelRelationshipSimChooser, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelRelationshipRelationshipType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelRelationshipRelationshipValue, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanelRelationshipButtons, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle(bundle.getString("ConsoleTitle")); // NOI18N
        setMinimumSize(Settings.MINIMUM_SIZE);
        setName("ConsoleGUI"); // NOI18N
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jToolBarCheatsBar.setRollover(true);
        jToolBarCheatsBar.setName(bundle.getString("jToolBarCheatsBarName")); // NOI18N

        jToggleButtonCheatsConsole.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jCheckBoxMenuItemCheatsConsoleMnemonic").charAt(0));
        jToggleButtonCheatsConsole.setText(bundle.getString("jCheckBoxMenuItemCheatsConsole")); // NOI18N
        jToggleButtonCheatsConsole.setFocusable(false);
        jToggleButtonCheatsConsole.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jToggleButtonCheatsConsole.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jToggleButtonCheatsConsole.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButtonCheatsConsoleActionPerformed(evt);
            }
        });
        jToolBarCheatsBar.add(jToggleButtonCheatsConsole);

        jToggleButtonCheatsExplorer.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jCheckBoxMenuItemCheatsExplorerMnemonic").charAt(0));
        jToggleButtonCheatsExplorer.setText(bundle.getString("jCheckBoxMenuItemCheatsExplorer")); // NOI18N
        jToggleButtonCheatsExplorer.setFocusable(false);
        jToggleButtonCheatsExplorer.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jToggleButtonCheatsExplorer.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jToggleButtonCheatsExplorer.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jToggleButtonCheatsExplorerActionPerformed(evt);
            }
        });
        jToolBarCheatsBar.add(jToggleButtonCheatsExplorer);

        jToolBarStatusBar.setBorder(javax.swing.BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.RAISED));
        jToolBarStatusBar.setFloatable(false);
        jToolBarStatusBar.setRollover(true);
        jToolBarStatusBar.add(filler1);

        jLabelConnectionStatus.setText(bundle.getString("jLabelConnectionStatusWaiting")); // NOI18N
        jToolBarStatusBar.add(jLabelConnectionStatus);
        jToolBarStatusBar.add(filler6);
        jToolBarStatusBar.add(jSeparator2);
        jToolBarStatusBar.add(filler3);
        jToolBarStatusBar.add(filler2);
        jToolBarStatusBar.add(jSeparator3);

        jPanelCommandStatus.setMaximumSize(new java.awt.Dimension(150, 23));
        jPanelCommandStatus.add(filler9);
        jPanelCommandStatus.add(filler10);

        jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusNone")); // NOI18N
        jPanelCommandStatus.add(jLabelCommandStatus);
        jLabelCommandStatus.getAccessibleContext().setAccessibleName("Command Status");

        jToolBarStatusBar.add(jPanelCommandStatus);
        jToolBarStatusBar.add(jSeparator1);
        jToolBarStatusBar.add(filler5);

        jProgressBarStatus.setIndeterminate(true);
        jProgressBarStatus.setMaximumSize(new java.awt.Dimension(100, 11));
        jProgressBarStatus.setMinimumSize(new java.awt.Dimension(100, 11));
        jToolBarStatusBar.add(jProgressBarStatus);
        jToolBarStatusBar.add(filler4);

        jDesktopPaneDesktop.setAutoscrolls(true);
        jDesktopPaneDesktop.addContainerListener(new java.awt.event.ContainerAdapter() {
            public void componentAdded(java.awt.event.ContainerEvent evt) {
                jDesktopPaneDesktopComponentAdded(evt);
            }
        });

        jInternalFrameConsole.setClosable(true);
        jInternalFrameConsole.setDefaultCloseOperation(javax.swing.WindowConstants.HIDE_ON_CLOSE);
        jInternalFrameConsole.setIconifiable(true);
        jInternalFrameConsole.setMaximizable(true);
        jInternalFrameConsole.setResizable(true);
        jInternalFrameConsole.setTitle(bundle.getString("jInternalFrameConsole")); // NOI18N
        jInternalFrameConsole.setToolTipText("");
        jInternalFrameConsole.setName("Cheat Console"); // NOI18N
        jInternalFrameConsole.setVisible(false);
        jInternalFrameConsole.addInternalFrameListener(new javax.swing.event.InternalFrameListener() {
            public void internalFrameActivated(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameClosed(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameClosing(javax.swing.event.InternalFrameEvent evt) {
                jInternalFrameConsoleInternalFrameClosing(evt);
            }
            public void internalFrameDeactivated(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameDeiconified(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameIconified(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameOpened(javax.swing.event.InternalFrameEvent evt) {
            }
        });

        jPanelConsole.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("jPanelConsole"))); // NOI18N

        jLabelConsolePrompt.setFont(new java.awt.Font("DejaVu Sans Mono", 1, 10)); // NOI18N
        jLabelConsolePrompt.setLabelFor(jTextFieldConsolePrompt);
        jLabelConsolePrompt.setText(">>>");

        jTextFieldConsolePrompt.setFont(new java.awt.Font("DejaVu Sans Mono", 0, 10)); // NOI18N
        jTextFieldConsolePrompt.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTextFieldConsolePromptKeyReleased(evt);
            }
        });

        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane1.setToolTipText("");
        jScrollPane1.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        jEditorPaneConsoleOutput.setEditable(false);
        jScrollPane1.setViewportView(jEditorPaneConsoleOutput);

        javax.swing.GroupLayout jPanelConsoleLayout = new javax.swing.GroupLayout(jPanelConsole);
        jPanelConsole.setLayout(jPanelConsoleLayout);
        jPanelConsoleLayout.setHorizontalGroup(
            jPanelConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelConsoleLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane1)
                    .addGroup(jPanelConsoleLayout.createSequentialGroup()
                        .addComponent(jLabelConsolePrompt, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTextFieldConsolePrompt, javax.swing.GroupLayout.DEFAULT_SIZE, 237, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanelConsoleLayout.setVerticalGroup(
            jPanelConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelConsoleLayout.createSequentialGroup()
                .addGroup(jPanelConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelConsolePrompt)
                    .addComponent(jTextFieldConsolePrompt, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 169, Short.MAX_VALUE)
                .addContainerGap())
        );

        var keywords = new ArrayList<String>();
        var autocomplete = new Autocomplete(jTextFieldConsolePrompt, keywords);

        jTextFieldConsolePrompt.setFocusTraversalKeysEnabled(true);
        jTextFieldConsolePrompt.getDocument().addDocumentListener(autocomplete);

        javax.swing.GroupLayout jInternalFrameConsoleLayout = new javax.swing.GroupLayout(jInternalFrameConsole.getContentPane());
        jInternalFrameConsole.getContentPane().setLayout(jInternalFrameConsoleLayout);
        jInternalFrameConsoleLayout.setHorizontalGroup(
            jInternalFrameConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jInternalFrameConsoleLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelConsole, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jInternalFrameConsoleLayout.setVerticalGroup(
            jInternalFrameConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jInternalFrameConsoleLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelConsole, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jDesktopPaneDesktop.add(jInternalFrameConsole);
        jInternalFrameConsole.setBounds(30, 10, 330, 270);

        jInternalFrameCheatsExplorer.setClosable(true);
        jInternalFrameCheatsExplorer.setDefaultCloseOperation(javax.swing.WindowConstants.HIDE_ON_CLOSE);
        jInternalFrameCheatsExplorer.setIconifiable(true);
        jInternalFrameCheatsExplorer.setMaximizable(true);
        jInternalFrameCheatsExplorer.setResizable(true);
        jInternalFrameCheatsExplorer.setTitle(bundle.getString("jInternalFrameCheatsExplorer")); // NOI18N
        jInternalFrameCheatsExplorer.setVisible(false);
        jInternalFrameCheatsExplorer.addInternalFrameListener(new javax.swing.event.InternalFrameListener() {
            public void internalFrameActivated(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameClosed(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameClosing(javax.swing.event.InternalFrameEvent evt) {
                jInternalFrameCheatsExplorerInternalFrameClosing(evt);
            }
            public void internalFrameDeactivated(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameDeiconified(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameIconified(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameOpened(javax.swing.event.InternalFrameEvent evt) {
            }
        });

        jSplitPane1.setDividerLocation(100);
        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        jScrollPane2.setPreferredSize(new java.awt.Dimension(335, 315));

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        jTreeCheatsByType.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        jTreeCheatsByType.setRequestFocusEnabled(false);
        jScrollPane2.setViewportView(jTreeCheatsByType);

        jSplitPane1.setLeftComponent(jScrollPane2);

        jTableCheatsByType.setEnabled(false);
        jScrollPane4.setViewportView(jTableCheatsByType);

        jSplitPane1.setRightComponent(jScrollPane4);

        javax.swing.GroupLayout jInternalFrameCheatsExplorerLayout = new javax.swing.GroupLayout(jInternalFrameCheatsExplorer.getContentPane());
        jInternalFrameCheatsExplorer.getContentPane().setLayout(jInternalFrameCheatsExplorerLayout);
        jInternalFrameCheatsExplorerLayout.setHorizontalGroup(
            jInternalFrameCheatsExplorerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jInternalFrameCheatsExplorerLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE)
                .addContainerGap())
        );
        jInternalFrameCheatsExplorerLayout.setVerticalGroup(
            jInternalFrameCheatsExplorerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jInternalFrameCheatsExplorerLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 112, Short.MAX_VALUE)
                .addContainerGap())
        );

        jDesktopPaneDesktop.add(jInternalFrameCheatsExplorer);
        jInternalFrameCheatsExplorer.setBounds(30, 20, 360, 230);

        jInternalFrameSettings.setClosable(true);
        jInternalFrameSettings.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        jInternalFrameSettings.setIconifiable(true);
        jInternalFrameSettings.setMaximizable(true);
        jInternalFrameSettings.setResizable(true);
        jInternalFrameSettings.setTitle(bundle.getString("jInternalFrameSettings")); // NOI18N
        jInternalFrameSettings.setMinimumSize(new java.awt.Dimension(480, 500));
        jInternalFrameSettings.setPreferredSize(new java.awt.Dimension(480, 500));
        jInternalFrameSettings.setVisible(false);
        jInternalFrameSettings.addInternalFrameListener(new javax.swing.event.InternalFrameListener() {
            public void internalFrameActivated(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameClosed(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameClosing(javax.swing.event.InternalFrameEvent evt) {
                jInternalFrameSettingsInternalFrameClosing(evt);
            }
            public void internalFrameDeactivated(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameDeiconified(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameIconified(javax.swing.event.InternalFrameEvent evt) {
            }
            public void internalFrameOpened(javax.swing.event.InternalFrameEvent evt) {
            }
        });

        jPanelSettingsConsoleGUI.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("jPanelSettingsConsoleGUI"))); // NOI18N

        jLabelSettingsLookAndFeel.setLabelFor(jComboBoxSettingsLookAndFeel);
        jLabelSettingsLookAndFeel.setText(bundle.getString("jLabelSettingsLookAndFeel")); // NOI18N

        jComboBoxSettingsLookAndFeel.setToolTipText(bundle.getString("jComboBoxLookAndFeelToolTip")); // NOI18N
        jComboBoxSettingsLookAndFeel.addItem("System");
        java.util.Arrays.asList(javax.swing.UIManager.getInstalledLookAndFeels()).forEach((var laf) -> this.jComboBoxSettingsLookAndFeel.addItem(laf.getName()));
        jComboBoxSettingsLookAndFeel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxSettingsLookAndFeelActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelSettingsConsoleGUILayout = new javax.swing.GroupLayout(jPanelSettingsConsoleGUI);
        jPanelSettingsConsoleGUI.setLayout(jPanelSettingsConsoleGUILayout);
        jPanelSettingsConsoleGUILayout.setHorizontalGroup(
            jPanelSettingsConsoleGUILayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsConsoleGUILayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabelSettingsLookAndFeel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jComboBoxSettingsLookAndFeel, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(11, 11, 11))
        );
        jPanelSettingsConsoleGUILayout.setVerticalGroup(
            jPanelSettingsConsoleGUILayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsConsoleGUILayout.createSequentialGroup()
                .addGroup(jPanelSettingsConsoleGUILayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSettingsLookAndFeel)
                    .addComponent(jComboBoxSettingsLookAndFeel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 10, Short.MAX_VALUE))
        );

        jPanelSettingsCheatsConsole.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("jPanelSettingsCheatsConsole"))); // NOI18N

        jLabelSettingsFontFamily.setLabelFor(jComboBoxSettingsFontFamily);
        jLabelSettingsFontFamily.setText(bundle.getString("jLabelSettingsFontFamily")); // NOI18N

        var frc = new java.awt.font.FontRenderContext(null,
            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_DEFAULT,
            java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_DEFAULT);
        var allFonts = java.util.Arrays.asList(
            java.awt.GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .getAvailableFontFamilyNames());
        allFonts.stream().filter((var fontName) -> {
            var font = java.awt.Font.decode(fontName).deriveFont(12);
            var iBounds = font.getStringBounds("i", frc);
            var mBounds = font.getStringBounds("m", frc);
            return (iBounds.getWidth() == mBounds.getWidth());
        }).forEach((var font) -> this.jComboBoxSettingsFontFamily.addItem(font));
        jComboBoxSettingsFontFamily.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxSettingsFontFamilyActionPerformed(evt);
            }
        });

        IntStream.rangeClosed(8, 24).forEach((var item) -> this.jComboBoxSettingsFontSize.addItem(item));
        jComboBoxSettingsFontSize.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxSettingsFontSizeActionPerformed(evt);
            }
        });

        jLabelSettingsFontSize.setLabelFor(jComboBoxSettingsFontSize);
        jLabelSettingsFontSize.setText(bundle.getString("jLabelSettingsFontSize")); // NOI18N

        jPanelSettingsEditorColour.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("jPanelSettingsEditorColour"))); // NOI18N

        jLabelSettingsEditorColourCategory.setLabelFor(jComboBoxSettingsEditorColourCategory);
        jLabelSettingsEditorColourCategory.setText(bundle.getString("jLabelSettingsEditorColourCategory")); // NOI18N

        jComboBoxSettingsEditorColourCategory.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxSettingsEditorColourCategoryActionPerformed(evt);
            }
        });

        jLabelSettingsEditorColour.setLabelFor(jFormattedTextFieldSettingsEditorColour);
        jLabelSettingsEditorColour.setText(bundle.getString("jLabelSettingsEditorColour")); // NOI18N

        jFormattedTextFieldSettingsEditorColour.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jFormattedTextFieldSettingsEditorColourKeyReleased(evt);
            }
        });

        jLabelColourHexHash.setText("#");

        jButtonSettingsChooseColour.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jButtonSettingsChooseColourMnemonic").charAt(0));
        jButtonSettingsChooseColour.setText(bundle.getString("jButtonSettingsChooseColour")); // NOI18N
        jButtonSettingsChooseColour.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSettingsChooseColourActionPerformed(evt);
            }
        });

        jLabelSettingsColourPreview.setText("    ");
        jLabelSettingsColourPreview.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        jLabelSettingsColourPreview.setOpaque(true);

        javax.swing.GroupLayout jPanelSettingsEditorColourLayout = new javax.swing.GroupLayout(jPanelSettingsEditorColour);
        jPanelSettingsEditorColour.setLayout(jPanelSettingsEditorColourLayout);
        jPanelSettingsEditorColourLayout.setHorizontalGroup(
            jPanelSettingsEditorColourLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsEditorColourLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsEditorColourLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jLabelSettingsEditorColourCategory)
                    .addComponent(jLabelSettingsEditorColour, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelSettingsEditorColourLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jComboBoxSettingsEditorColourCategory, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanelSettingsEditorColourLayout.createSequentialGroup()
                        .addComponent(jLabelColourHexHash, javax.swing.GroupLayout.PREFERRED_SIZE, 7, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jFormattedTextFieldSettingsEditorColour, javax.swing.GroupLayout.DEFAULT_SIZE, 151, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabelSettingsColourPreview, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButtonSettingsChooseColour)))
                .addContainerGap())
        );
        jPanelSettingsEditorColourLayout.setVerticalGroup(
            jPanelSettingsEditorColourLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsEditorColourLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsEditorColourLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSettingsEditorColourCategory)
                    .addComponent(jComboBoxSettingsEditorColourCategory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelSettingsEditorColourLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSettingsEditorColour)
                    .addComponent(jFormattedTextFieldSettingsEditorColour, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelColourHexHash)
                    .addComponent(jButtonSettingsChooseColour)
                    .addComponent(jLabelSettingsColourPreview))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jScrollPane3.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        jEditorPaneSettingsAppearancePreview.setEditable(false);
        jScrollPane3.setViewportView(jEditorPaneSettingsAppearancePreview);

        jLabelSettingsPreview.setLabelFor(jScrollPane3);
        jLabelSettingsPreview.setText(bundle.getString("jLabelSettingsPreview")); // NOI18N

        javax.swing.GroupLayout jPanelSettingsCheatsConsoleLayout = new javax.swing.GroupLayout(jPanelSettingsCheatsConsole);
        jPanelSettingsCheatsConsole.setLayout(jPanelSettingsCheatsConsoleLayout);
        jPanelSettingsCheatsConsoleLayout.setHorizontalGroup(
            jPanelSettingsCheatsConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsCheatsConsoleLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsCheatsConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelSettingsEditorColour, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane3)
                    .addGroup(jPanelSettingsCheatsConsoleLayout.createSequentialGroup()
                        .addGroup(jPanelSettingsCheatsConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jLabelSettingsFontFamily, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabelSettingsFontSize, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelSettingsCheatsConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jComboBoxSettingsFontSize, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jComboBoxSettingsFontFamily, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addComponent(jLabelSettingsPreview, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelSettingsCheatsConsoleLayout.setVerticalGroup(
            jPanelSettingsCheatsConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsCheatsConsoleLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsCheatsConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboBoxSettingsFontFamily, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelSettingsFontFamily))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelSettingsCheatsConsoleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jComboBoxSettingsFontSize, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelSettingsFontSize))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanelSettingsEditorColour, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(2, 2, 2)
                .addComponent(jLabelSettingsPreview)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 110, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanelSettingsAppearanceLayout = new javax.swing.GroupLayout(jPanelSettingsAppearance);
        jPanelSettingsAppearance.setLayout(jPanelSettingsAppearanceLayout);
        jPanelSettingsAppearanceLayout.setHorizontalGroup(
            jPanelSettingsAppearanceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsAppearanceLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsAppearanceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelSettingsCheatsConsole, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelSettingsConsoleGUI, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelSettingsAppearanceLayout.setVerticalGroup(
            jPanelSettingsAppearanceLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsAppearanceLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelSettingsConsoleGUI, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelSettingsCheatsConsole, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPaneSettings.addTab(bundle.getString("jPanelAppearance"), null, jPanelSettingsAppearance, bundle.getString("jPanelAppearanceToolTip")); // NOI18N
        jPanelSettingsAppearance.getAccessibleContext().setAccessibleName("");

        jPanelSettingsMenuBindings.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("jPanelSettingsMenuBindings"))); // NOI18N

        jLabelSettingsMenuItem.setLabelFor(jComboBoxSettingsMenuItem);
        jLabelSettingsMenuItem.setText(bundle.getString("jLabelSettingsMenuItem")); // NOI18N

        jComboBoxSettingsMenuItem.setDoubleBuffered(true);
        jComboBoxSettingsMenuItem.setRenderer(new MenuItemListCellRenderer());

        jPanelSettingsMenuItemShortcut.setBorder(javax.swing.BorderFactory.createTitledBorder(""));

        jLabelSettingsKey.setLabelFor(jComboBoxSettingsShortcutKey);
        jLabelSettingsKey.setText(bundle.getString("jLabelSettingsKey")); // NOI18N

        var noItem = new VirtualKey(java.util.ResourceBundle.getBundle("ConsoleGUI").getString("jComboBoxSettingsShortcutKeyNone"), -1);
        jComboBoxSettingsShortcutKey.addItem(noItem);
        jComboBoxSettingsShortcutKey.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxSettingsShortcutKeyActionPerformed(evt);
            }
        });

        jCheckBoxSettingsCtrl.setText(bundle.getString("jCheckBoxSettingsCtrl")); // NOI18N
        jCheckBoxSettingsCtrl.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxSettingsCtrlActionPerformed(evt);
            }
        });
        jPanelSettingsShortcutControlKeys.add(jCheckBoxSettingsCtrl);

        jCheckBoxSettingsShift.setText(bundle.getString("jCheckBoxSettingsShift")); // NOI18N
        jCheckBoxSettingsShift.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxSettingsShiftActionPerformed(evt);
            }
        });
        jPanelSettingsShortcutControlKeys.add(jCheckBoxSettingsShift);

        jCheckBoxSettingsAlt.setText(bundle.getString("jCheckBoxSettingsAlt")); // NOI18N
        jCheckBoxSettingsAlt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxSettingsAltActionPerformed(evt);
            }
        });
        jPanelSettingsShortcutControlKeys.add(jCheckBoxSettingsAlt);

        jLabelSettingsMenuItemKeyStroke.setText(bundle.getString("jLabelSettingsMenuItemKeyStroke")); // NOI18N
        jLabelSettingsMenuItemKeyStroke.setMaximumSize(new java.awt.Dimension(62, 13));
        jLabelSettingsMenuItemKeyStroke.setMinimumSize(new java.awt.Dimension(62, 13));

        jTextFieldKeyStroke.setKeymap(null);
        jTextFieldKeyStroke.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                jTextFieldKeyStrokeKeyPressed(evt);
            }
        });

        javax.swing.GroupLayout jPanelSettingsMenuItemShortcutLayout = new javax.swing.GroupLayout(jPanelSettingsMenuItemShortcut);
        jPanelSettingsMenuItemShortcut.setLayout(jPanelSettingsMenuItemShortcutLayout);
        jPanelSettingsMenuItemShortcutLayout.setHorizontalGroup(
            jPanelSettingsMenuItemShortcutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsMenuItemShortcutLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsMenuItemShortcutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelSettingsShortcutControlKeys, javax.swing.GroupLayout.DEFAULT_SIZE, 367, Short.MAX_VALUE)
                    .addGroup(jPanelSettingsMenuItemShortcutLayout.createSequentialGroup()
                        .addComponent(jLabelSettingsKey)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBoxSettingsShortcutKey, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanelSettingsMenuItemShortcutLayout.createSequentialGroup()
                        .addComponent(jLabelSettingsMenuItemKeyStroke, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jTextFieldKeyStroke)))
                .addContainerGap())
        );
        jPanelSettingsMenuItemShortcutLayout.setVerticalGroup(
            jPanelSettingsMenuItemShortcutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsMenuItemShortcutLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsMenuItemShortcutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSettingsKey)
                    .addComponent(jComboBoxSettingsShortcutKey, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanelSettingsShortcutControlKeys, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanelSettingsMenuItemShortcutLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSettingsMenuItemKeyStroke, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jTextFieldKeyStroke, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        final java.util.HashSet<String> blackList = new java.util.HashSet<>();
        blackList.add("Ctrl");
        blackList.add("Alt");
        blackList.add("Shift");
        blackList.add("Meta");
        blackList.add("NumPad");
        blackList.add("Dead");
        blackList.add("Final");
        blackList.add("Being");
        blackList.add("End");
        blackList.add("Convert");
        blackList.add("Japanese");
        blackList.add("Kana");
        blackList.add("Kanji");
        blackList.add("Hiragana");
        blackList.add("Katakana");
        blackList.add("Euro");
        blackList.add("Mode Change");
        blackList.add("Alphanumeric");
        blackList.add("Compose");
        blackList.add("Begin");
        blackList.add("Stop");
        blackList.add("Props");
        blackList.add("Find");
        blackList.add("Copy");
        blackList.add("Paste");
        blackList.add("Cut");
        blackList.add("Undo");
        blackList.add("Again");
        blackList.add("Input");
        blackList.add("Roman Characters");
        blackList.add("Width");
        blackList.add("Candidate");
        blackList.add("Accept");
        blackList.add("Context Menu");
        blackList.add("Clear");
        blackList.add("Cancel");
        blackList.add("Pause");
        blackList.add("Unknown");
        blackList.add("Inverted");

        for (int i = 13; i <= 24; i++)
        blackList.add(String.format("F%d", i));

        this.virtualKeys = new HashMap<>();
        virtualKeys.put(-1, noItem);

        java.util.Arrays.asList(
            java.awt.event.KeyEvent.class.getFields()
        ).stream().filter((var field) -> field.getName().startsWith("VK_")).forEach((var field) -> {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                var name = field.getName();
                final VirtualKey virtualKey = new VirtualKey();

                try {
                    var code = field.getInt(name);
                    virtualKey.name = java.awt.event.KeyEvent.getKeyText(code);
                    virtualKey.keyCode = code;
                }
                catch (Exception ex) {
                    virtualKey.name = name;
                }

                final String item = virtualKey.name;
                if (!blackList.stream().anyMatch((var blackListItem) -> item.contains(blackListItem))) {
                    jComboBoxSettingsShortcutKey.addItem(virtualKey);
                    virtualKeys.put(virtualKey.keyCode, virtualKey);
                }
            }
        });

        javax.swing.GroupLayout jPanelSettingsMenuBindingsLayout = new javax.swing.GroupLayout(jPanelSettingsMenuBindings);
        jPanelSettingsMenuBindings.setLayout(jPanelSettingsMenuBindingsLayout);
        jPanelSettingsMenuBindingsLayout.setHorizontalGroup(
            jPanelSettingsMenuBindingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsMenuBindingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsMenuBindingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanelSettingsMenuBindingsLayout.createSequentialGroup()
                        .addComponent(jLabelSettingsMenuItem)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jComboBoxSettingsMenuItem, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jPanelSettingsMenuItemShortcut, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelSettingsMenuBindingsLayout.setVerticalGroup(
            jPanelSettingsMenuBindingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsMenuBindingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsMenuBindingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jComboBoxSettingsMenuItem)
                    .addComponent(jLabelSettingsMenuItem, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanelSettingsMenuItemShortcut, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jComboBoxSettingsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            private MenuItem currentItem;
            private javax.swing.JComboBox<MenuItem> parent;

            {
                this.parent = ConsoleGUI.this.jComboBoxSettingsMenuItem;
                this.currentItem = null;
            }

            public void actionPerformed(java.awt.event.ActionEvent evt) {
                var selectedItem = this.parent.getSelectedItem();
                if (selectedItem == null)
                return;

                if (selectedItem instanceof MenuItemSeparator) {
                    this.parent.setSelectedItem(this.currentItem);
                }
                else {
                    this.currentItem = (MenuItem) selectedItem;
                    jComboBoxSettingsMenuItemActionPerformed(evt);
                }
            }
        });

        jPanelSystemTrayMenu.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("jPanelSystemTrayMenu"))); // NOI18N

        jLabelSystemTrayMenu.setLabelFor(jListSystemTrayMenu);
        jLabelSystemTrayMenu.setText(bundle.getString("jLabelSystemTrayMenu")); // NOI18N

        jListSystemTrayMenu.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        jListSystemTrayMenu.addMouseListener(new java.awt.event.MouseAdapter() {
            private CheckListItem currentItem;
            private javax.swing.JList<CheckListItem> parent;

            {
                this.parent = ConsoleGUI.this.jListSystemTrayMenu;
                this.currentItem = null;
            }

            public void mouseClicked(java.awt.event.MouseEvent evt) {
                var selectedItem = this.parent.getSelectedValue();
                if (selectedItem == null)
                return;

                if (selectedItem instanceof CheckListItemSeparator)
                this.parent.setSelectedValue(this.currentItem, true);
                else {
                    this.currentItem = (CheckListItem) selectedItem;
                    jListSystemTrayMenuMouseClicked(evt);
                }
            }
        });
        jListSystemTrayMenu.setCellRenderer(new CheckListRenderer());
        jListSystemTrayMenu.setDoubleBuffered(true);
        jScrollPane6.setViewportView(jListSystemTrayMenu);

        javax.swing.GroupLayout jPanelSystemTrayMenuLayout = new javax.swing.GroupLayout(jPanelSystemTrayMenu);
        jPanelSystemTrayMenu.setLayout(jPanelSystemTrayMenuLayout);
        jPanelSystemTrayMenuLayout.setHorizontalGroup(
            jPanelSystemTrayMenuLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSystemTrayMenuLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSystemTrayMenuLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabelSystemTrayMenu, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.DEFAULT_SIZE, 391, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelSystemTrayMenuLayout.setVerticalGroup(
            jPanelSystemTrayMenuLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSystemTrayMenuLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabelSystemTrayMenu)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane6, javax.swing.GroupLayout.DEFAULT_SIZE, 127, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanelSettingsKeyBindingsLayout = new javax.swing.GroupLayout(jPanelSettingsKeyBindings);
        jPanelSettingsKeyBindings.setLayout(jPanelSettingsKeyBindingsLayout);
        jPanelSettingsKeyBindingsLayout.setHorizontalGroup(
            jPanelSettingsKeyBindingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsKeyBindingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelSettingsMenuBindings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
            .addGroup(jPanelSettingsKeyBindingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(jPanelSettingsKeyBindingsLayout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(jPanelSystemTrayMenu, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addContainerGap()))
        );
        jPanelSettingsKeyBindingsLayout.setVerticalGroup(
            jPanelSettingsKeyBindingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsKeyBindingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelSettingsMenuBindings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(204, Short.MAX_VALUE))
            .addGroup(jPanelSettingsKeyBindingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelSettingsKeyBindingsLayout.createSequentialGroup()
                    .addGap(200, 200, 200)
                    .addComponent(jPanelSystemTrayMenu, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addContainerGap()))
        );

        jTabbedPaneSettings.addTab(bundle.getString("jPanelSettingsKeyBindings"), jPanelSettingsKeyBindings); // NOI18N

        javax.swing.GroupLayout jPanelToolbarLayout = new javax.swing.GroupLayout(jPanelToolbar);
        jPanelToolbar.setLayout(jPanelToolbarLayout);
        jPanelToolbarLayout.setHorizontalGroup(
            jPanelToolbarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 443, Short.MAX_VALUE)
        );
        jPanelToolbarLayout.setVerticalGroup(
            jPanelToolbarLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 397, Short.MAX_VALUE)
        );

        jTabbedPaneSettings.addTab(bundle1.getString("jPanelToolbar"), jPanelToolbar); // NOI18N

        javax.swing.GroupLayout jPanelSettingsEditorLayout = new javax.swing.GroupLayout(jPanelSettingsEditor);
        jPanelSettingsEditor.setLayout(jPanelSettingsEditorLayout);
        jPanelSettingsEditorLayout.setHorizontalGroup(
            jPanelSettingsEditorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 443, Short.MAX_VALUE)
        );
        jPanelSettingsEditorLayout.setVerticalGroup(
            jPanelSettingsEditorLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 397, Short.MAX_VALUE)
        );

        jTabbedPaneSettings.addTab(bundle.getString("jPanelSettingsEditor"), jPanelSettingsEditor); // NOI18N

        jPanelSettingsGeneral.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("jPanelSettingsGeneral"))); // NOI18N

        jCheckBoxSettingsOpenAtReady.setText(bundle.getString("jCheckBoxSettingsOpenAtReady")); // NOI18N
        jCheckBoxSettingsOpenAtReady.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxSettingsOpenAtReadyActionPerformed(evt);
            }
        });

        jPanelSettingsTimeOut.setLayout(new java.awt.BorderLayout());

        jCheckBoxSettingsUseMaxTimeout.setText(bundle1.getString("jCheckBoxSettingsUseMaxTimeout")); // NOI18N
        jCheckBoxSettingsUseMaxTimeout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxSettingsUseMaxTimeoutActionPerformed(evt);
            }
        });
        jPanelSettingsTimeOut.add(jCheckBoxSettingsUseMaxTimeout, java.awt.BorderLayout.CENTER);

        jTextFieldMaxTimeOut.setColumns(10);
        jTextFieldMaxTimeOut.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTextFieldMaxTimeOutKeyReleased(evt);
            }
        });
        jPanelSettingsTimeOut.add(jTextFieldMaxTimeOut, java.awt.BorderLayout.EAST);

        jCheckBoxSettingsUSeNonEssentialQueries.setText(bundle1.getString("jCheckBoxSettingsUseNonEssentialQueries")); // NOI18N
        jCheckBoxSettingsUSeNonEssentialQueries.setToolTipText(bundle1.getString("jCheckBoxSettingsUseNonEssentialQueriesToolTip")); // NOI18N
        jCheckBoxSettingsUSeNonEssentialQueries.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxSettingsUSeNonEssentialQueriesActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanelSettingsGeneralLayout = new javax.swing.GroupLayout(jPanelSettingsGeneral);
        jPanelSettingsGeneral.setLayout(jPanelSettingsGeneralLayout);
        jPanelSettingsGeneralLayout.setHorizontalGroup(
            jPanelSettingsGeneralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsGeneralLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsGeneralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSeparator16, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanelSettingsGeneralLayout.createSequentialGroup()
                        .addGroup(jPanelSettingsGeneralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jCheckBoxSettingsUSeNonEssentialQueries, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jCheckBoxSettingsOpenAtReady, javax.swing.GroupLayout.DEFAULT_SIZE, 391, Short.MAX_VALUE)
                            .addComponent(jPanelSettingsTimeOut, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addContainerGap())))
        );
        jPanelSettingsGeneralLayout.setVerticalGroup(
            jPanelSettingsGeneralLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsGeneralLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jCheckBoxSettingsOpenAtReady)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelSettingsTimeOut, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator16, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jCheckBoxSettingsUSeNonEssentialQueries)
                .addContainerGap())
        );

        jPanelSettingsSizing.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("jPanelSettingsSizing"))); // NOI18N

        jCheckBoxSettingsSaveWindowSize.setText(bundle.getString("jCheckBoxSettingsSaveWindowSize")); // NOI18N
        jCheckBoxSettingsSaveWindowSize.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxSettingsSaveWindowSizeActionPerformed(evt);
            }
        });

        jLabelSettingsWindowHeight.setLabelFor(jTextFieldSettingsWindowHeight);
        jLabelSettingsWindowHeight.setText(bundle.getString("jLabelSettingsWindowHeight")); // NOI18N

        jTextFieldSettingsWindowHeight.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTextFieldSettingsWindowHeightKeyReleased(evt);
            }
        });

        jLabelSettingsWindowWidth.setText(bundle.getString("jLabelSettingsWindowWidth")); // NOI18N

        jTextFieldSettingsWindowWidth.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                jTextFieldSettingsWindowWidthKeyReleased(evt);
            }
        });

        javax.swing.GroupLayout jPanelSettingsSizingLayout = new javax.swing.GroupLayout(jPanelSettingsSizing);
        jPanelSettingsSizing.setLayout(jPanelSettingsSizingLayout);
        jPanelSettingsSizingLayout.setHorizontalGroup(
            jPanelSettingsSizingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsSizingLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsSizingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jCheckBoxSettingsSaveWindowSize, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanelSettingsSizingLayout.createSequentialGroup()
                        .addGroup(jPanelSettingsSizingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(jLabelSettingsWindowHeight, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabelSettingsWindowWidth, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanelSettingsSizingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jTextFieldSettingsWindowWidth, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jTextFieldSettingsWindowHeight))))
                .addContainerGap())
        );
        jPanelSettingsSizingLayout.setVerticalGroup(
            jPanelSettingsSizingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsSizingLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jCheckBoxSettingsSaveWindowSize)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelSettingsSizingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabelSettingsWindowHeight)
                    .addComponent(jTextFieldSettingsWindowHeight, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanelSettingsSizingLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jTextFieldSettingsWindowWidth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabelSettingsWindowWidth))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanelInitialSettings.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle1.getString("jPanelInitialSettings"))); // NOI18N

        jListInitialSettings.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        jListInitialSettings.setCellRenderer(new InitialSettingsItemListCellRenderer());
        jListInitialSettings.setDoubleBuffered(true);
        jListInitialSettings.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jListInitialSettingsMouseClicked(evt);
            }
        });
        jScrollPane8.setViewportView(jListInitialSettings);

        jPanelMCCC.setLayout(new java.awt.BorderLayout());

        jCheckBoxMCCC.setText(bundle1.getString("jCheckBoxMCCC")); // NOI18N
        jPanelMCCC.add(jCheckBoxMCCC, java.awt.BorderLayout.CENTER);
        jCheckBoxMCCC.addActionListener((evt) -> {
            var state = ConsoleGUI.settings.mcccSettings != null;
            ((javax.swing.JCheckBox)evt.getSource()).setSelected(state);
        });

        jButtonMCCC.setText(bundle1.getString("jButtonMCCC")); // NOI18N
        jButtonMCCC.setEnabled(false);
        jButtonMCCC.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonMCCCActionPerformed(evt);
            }
        });
        jPanelMCCC.add(jButtonMCCC, java.awt.BorderLayout.EAST);

        jPanelTMEX.setLayout(new java.awt.BorderLayout());

        jCheckBoxTMEX.setText(bundle1.getString("jCheckBoxTMEX")); // NOI18N
        jPanelTMEX.add(jCheckBoxTMEX, java.awt.BorderLayout.CENTER);
        jCheckBoxTMEX.addActionListener((evt) -> {
            var state = ConsoleGUI.settings.tmexSettings != null;
            ((javax.swing.JCheckBox)evt.getSource()).setSelected(state);
        });

        jButtonTMEX.setText(bundle1.getString("jButtonTMEX")); // NOI18N
        jButtonTMEX.setEnabled(false);
        jButtonTMEX.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonTMEXActionPerformed(evt);
            }
        });
        jPanelTMEX.add(jButtonTMEX, java.awt.BorderLayout.EAST);

        javax.swing.GroupLayout jPanelInitialSettingsLayout = new javax.swing.GroupLayout(jPanelInitialSettings);
        jPanelInitialSettings.setLayout(jPanelInitialSettingsLayout);
        jPanelInitialSettingsLayout.setHorizontalGroup(
            jPanelInitialSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelInitialSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelInitialSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane8)
                    .addComponent(jPanelMCCC, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelTMEX, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelInitialSettingsLayout.setVerticalGroup(
            jPanelInitialSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanelInitialSettingsLayout.createSequentialGroup()
                .addComponent(jPanelMCCC, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelTMEX, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane8, javax.swing.GroupLayout.DEFAULT_SIZE, 52, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanelSettingsMiscellaneousLayout = new javax.swing.GroupLayout(jPanelSettingsMiscellaneous);
        jPanelSettingsMiscellaneous.setLayout(jPanelSettingsMiscellaneousLayout);
        jPanelSettingsMiscellaneousLayout.setHorizontalGroup(
            jPanelSettingsMiscellaneousLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsMiscellaneousLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanelSettingsMiscellaneousLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelSettingsGeneral, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelSettingsSizing, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanelInitialSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanelSettingsMiscellaneousLayout.setVerticalGroup(
            jPanelSettingsMiscellaneousLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanelSettingsMiscellaneousLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanelSettingsGeneral, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelSettingsSizing, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelInitialSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPaneSettings.addTab(bundle.getString("jPanelSettingsMiscellaneous"), jPanelSettingsMiscellaneous); // NOI18N

        jPanelSettingsButtons.setLayout(new java.awt.BorderLayout());

        jPanelSettingsImportExport.setLayout(new java.awt.GridLayout(1, 0));

        jButtonSettingsExport.setText(bundle.getString("jButtonSettingsExport")); // NOI18N
        jButtonSettingsExport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSettingsExportActionPerformed(evt);
            }
        });
        jPanelSettingsImportExport.add(jButtonSettingsExport);

        jButtonSettingsImport.setText(bundle.getString("jButtonSettingsImport")); // NOI18N
        jButtonSettingsImport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSettingsImportActionPerformed(evt);
            }
        });
        jPanelSettingsImportExport.add(jButtonSettingsImport);

        jPanelSettingsButtons.add(jPanelSettingsImportExport, java.awt.BorderLayout.WEST);

        jPanelSettingsOkApplyCancel.setLayout(new java.awt.GridLayout(1, 0));

        jButtonSettingsOK.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jButtonSettingsOkMnemonic").charAt(0));
        jButtonSettingsOK.setText(bundle.getString("jButtonSettingsOk")); // NOI18N
        jButtonSettingsOK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSettingsOKActionPerformed(evt);
            }
        });
        jPanelSettingsOkApplyCancel.add(jButtonSettingsOK);

        jButtonSettingsApply.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jButtonSettingsApplyMnemonic").charAt(0));
        jButtonSettingsApply.setText(bundle.getString("jButtonSettingsApply")); // NOI18N
        jButtonSettingsApply.setEnabled(false);
        jButtonSettingsApply.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSettingsApplyActionPerformed(evt);
            }
        });
        jPanelSettingsOkApplyCancel.add(jButtonSettingsApply);

        jButtonSettingsCancel.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jButtonSettingsCancelMnemonic").charAt(0));
        jButtonSettingsCancel.setText(bundle.getString("jButtonSettingsCancel")); // NOI18N
        jButtonSettingsCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonSettingsCancelActionPerformed(evt);
            }
        });
        jPanelSettingsOkApplyCancel.add(jButtonSettingsCancel);

        jPanelSettingsButtons.add(jPanelSettingsOkApplyCancel, java.awt.BorderLayout.EAST);

        javax.swing.GroupLayout jInternalFrameSettingsLayout = new javax.swing.GroupLayout(jInternalFrameSettings.getContentPane());
        jInternalFrameSettings.getContentPane().setLayout(jInternalFrameSettingsLayout);
        jInternalFrameSettingsLayout.setHorizontalGroup(
            jInternalFrameSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jInternalFrameSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jInternalFrameSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanelSettingsButtons, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jTabbedPaneSettings))
                .addContainerGap())
        );
        jInternalFrameSettingsLayout.setVerticalGroup(
            jInternalFrameSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jInternalFrameSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPaneSettings)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanelSettingsButtons, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jDesktopPaneDesktop.add(jInternalFrameSettings);
        jInternalFrameSettings.setBounds(20, 10, 390, 290);

        jMenuFile.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jMenuFileMnemonic").charAt(0));
        jMenuFile.setText(bundle.getString("jMenuFile")); // NOI18N

        jMenuItemOptions.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
        jMenuItemOptions.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jMenuItemOptionsMnemonic").charAt(0));
        jMenuItemOptions.setText(bundle.getString("jMenuItemOptions")); // NOI18N
        jMenuItemOptions.setName("jMenuItemOptions"); // NOI18N
        jMenuItemOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemOptionsActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemOptions);
        jMenuFile.add(jSeparator4);

        jMenuItemExit.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_MASK));
        jMenuItemExit.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jMenuItemExitMnemonic").charAt(0));
        jMenuItemExit.setText(bundle.getString("jMenuItemExit")); // NOI18N
        jMenuItemExit.setName("jMenuItemExit"); // NOI18N
        jMenuItemExit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemExitActionPerformed(evt);
            }
        });
        jMenuFile.add(jMenuItemExit);

        jMenuBarMain.add(jMenuFile);

        jMenuView.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jMenuViewMnemomic").charAt(0));
        jMenuView.setText(bundle.getString("jMenuView")); // NOI18N

        jCheckBoxMenuItemCheatsConsole.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jCheckBoxMenuItemCheatsConsole.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jCheckBoxMenuItemCheatsConsoleMnemonic").charAt(0));
        jCheckBoxMenuItemCheatsConsole.setText(bundle.getString("jCheckBoxMenuItemCheatsConsole")); // NOI18N
        jCheckBoxMenuItemCheatsConsole.setName("jCheckBoxMenuItemCheatsConsole"); // NOI18N
        jCheckBoxMenuItemCheatsConsole.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxMenuItemCheatsConsoleActionPerformed(evt);
            }
        });
        jMenuView.add(jCheckBoxMenuItemCheatsConsole);

        jCheckBoxMenuItemCheatsExplorer.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_E, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jCheckBoxMenuItemCheatsExplorer.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jCheckBoxMenuItemCheatsExplorerMnemonic").charAt(0));
        jCheckBoxMenuItemCheatsExplorer.setText(bundle.getString("jCheckBoxMenuItemCheatsExplorer")); // NOI18N
        jCheckBoxMenuItemCheatsExplorer.setName("jCheckBoxMenuItemCheatsExplorer"); // NOI18N
        jCheckBoxMenuItemCheatsExplorer.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jCheckBoxMenuItemCheatsExplorerActionPerformed(evt);
            }
        });
        jMenuView.add(jCheckBoxMenuItemCheatsExplorer);

        jMenuBarMain.add(jMenuView);

        jMenuCheats.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jMenuCheatsMnemonic").charAt(0));
        jMenuCheats.setText(bundle.getString("jMenuCheats")); // NOI18N

        jMenuToggles.setText(bundle.getString("jMenuToggles")); // NOI18N

        jCheckBoxMenuItemTestingCheats.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jCheckBoxMenuItemTestingCheats.setText(bundle.getString("jCheckBoxMenuItemTestingCheats")); // NOI18N
        jMenuToggles.add(jCheckBoxMenuItemTestingCheats);
        jMenuToggles.add(jSeparator7);

        jCheckBoxMenuItemHoverEffects.setText(bundle.getString("jCheckBoxMenuItemHoverEffects")); // NOI18N
        jMenuToggles.add(jCheckBoxMenuItemHoverEffects);

        jCheckBoxMenuItemHeadlineEffects.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jCheckBoxMenuItemHeadlineEffects.setText(bundle.getString("jCheckBoxMenuItemHeadlineEffects")); // NOI18N
        jMenuToggles.add(jCheckBoxMenuItemHeadlineEffects);
        jMenuToggles.add(jSeparator8);

        jCheckBoxMenuItemMoveObjects.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.CTRL_MASK));
        jCheckBoxMenuItemMoveObjects.setText(bundle.getString("jCheckBoxMenuItemMoveObjects")); // NOI18N
        jMenuToggles.add(jCheckBoxMenuItemMoveObjects);

        jCheckBoxMenuItemHiddenObjects.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H, java.awt.event.InputEvent.CTRL_MASK));
        jCheckBoxMenuItemHiddenObjects.setText(bundle.getString("jCheckBoxMenuItemHiddenObjects")); // NOI18N
        jMenuToggles.add(jCheckBoxMenuItemHiddenObjects);

        jCheckBoxMenuItemGamePlayUnlocks.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_U, java.awt.event.InputEvent.CTRL_MASK));
        jCheckBoxMenuItemGamePlayUnlocks.setText(bundle.getString("jCheckBoxMenuItemGamePlayUnlocks")); // NOI18N
        jMenuToggles.add(jCheckBoxMenuItemGamePlayUnlocks);

        jCheckBoxMenuItemFreeRealEstate.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.CTRL_MASK));
        jCheckBoxMenuItemFreeRealEstate.setText(bundle.getString("jCheckBoxMenuItemFreeRealEstate")); // NOI18N
        jMenuToggles.add(jCheckBoxMenuItemFreeRealEstate);
        jMenuToggles.add(jSeparator9);

        jCheckBoxMenuItemFullEditMode.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_MASK));
        jCheckBoxMenuItemFullEditMode.setText(bundle.getString("jCheckBoxMenuItemFullEditMode")); // NOI18N
        jMenuToggles.add(jCheckBoxMenuItemFullEditMode);

        jMenuCheats.add(jMenuToggles);
        for (var toggleMenuItem : jMenuToggles.getMenuComponents()) {
            if (toggleMenuItem instanceof javax.swing.JMenuItem) {
                ((javax.swing.JMenuItem) toggleMenuItem).addActionListener(
                    (evt) -> toggleMenuItemActionPerformed(evt));
            }
        }
        jMenuCheats.add(jSeparator10);

        jMenuMoney.setText(bundle.getString("jMenuMoney")); // NOI18N

        jMenuItemMotherlode.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemMotherlode.setText(bundle.getString("jMenuItemMotherlode")); // NOI18N
        jMenuItemMotherlode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemMotherlodeActionPerformed(evt);
            }
        });
        jMenuMoney.add(jMenuItemMotherlode);

        jMenuItemRosebud.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_R, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemRosebud.setText(bundle.getString("jMenuItemRosebud")); // NOI18N
        jMenuItemRosebud.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemRosebudActionPerformed(evt);
            }
        });
        jMenuMoney.add(jMenuItemRosebud);
        jMenuMoney.add(jSeparator14);

        jMenuItemClearMoney.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_0, java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemClearMoney.setText("Clear Money");
        jMenuItemClearMoney.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemClearMoneyActionPerformed(evt);
            }
        });
        jMenuMoney.add(jMenuItemClearMoney);

        jMenuItemSetMoney.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_4, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemSetMoney.setText(bundle.getString("jMenuItemSetMoney")); // NOI18N
        jMenuItemSetMoney.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemSetMoneyActionPerformed(evt);
            }
        });
        jMenuMoney.add(jMenuItemSetMoney);
        jMenuMoney.add(jSeparator15);

        jMenuItemDepositMoney.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_EQUALS, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemDepositMoney.setText(bundle.getString("jMenuItemDepositMoney")); // NOI18N
        jMenuItemDepositMoney.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemDepositMoneyActionPerformed(evt);
            }
        });
        jMenuMoney.add(jMenuItemDepositMoney);

        jMenuItemWithdrawMoney.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_MINUS, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemWithdrawMoney.setText(bundle.getString("jMenuItemWithdrawMoney")); // NOI18N
        jMenuItemWithdrawMoney.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemWithdrawMoneyActionPerformed(evt);
            }
        });
        jMenuMoney.add(jMenuItemWithdrawMoney);

        jMenuCheats.add(jMenuMoney);
        jMenuCheats.add(jSeparator12);

        jMenuSkillCheats.setText(bundle.getString("jMenuSkillCheats")); // NOI18N

        jMenuItemSetSkill.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemSetSkill.setText(bundle.getString("jMenuItemSetSkill")); // NOI18N
        jMenuSkillCheats.add(jMenuItemSetSkill);

        jMenuItemClearAllSkills.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, java.awt.event.InputEvent.ALT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        jMenuItemClearAllSkills.setText(bundle.getString("jMenuItemClearAllSkills")); // NOI18N
        jMenuSkillCheats.add(jMenuItemClearAllSkills);

        jMenuCheats.add(jMenuSkillCheats);

        jMenuCareerCheats.setText(bundle.getString("jMenuCareerCheats")); // NOI18N

        jMenuItemPromote.setText(bundle.getString("jMenuItemPromote")); // NOI18N
        jMenuCareerCheats.add(jMenuItemPromote);

        jMenuItemDemote.setText(bundle.getString("jMenuItemDemote")); // NOI18N
        jMenuCareerCheats.add(jMenuItemDemote);

        jMenuCheats.add(jMenuCareerCheats);
        jMenuCheats.add(jSeparator11);

        jMenuBuildBuy.setText(bundle.getString("jMenuBuildBuy")); // NOI18N

        jMenuItemEnableFreeBuild.setText(bundle.getString("jMenuItemEnableFreeBuild")); // NOI18N
        jMenuItemEnableFreeBuild.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemEnableFreeBuildActionPerformed(evt);
            }
        });
        jMenuBuildBuy.add(jMenuItemEnableFreeBuild);

        jMenuCheats.add(jMenuBuildBuy);
        jMenuCheats.add(jSeparator13);

        jMenuRelationshipCheats.setText(bundle.getString("jMenuRelationshipCheats")); // NOI18N

        jMenuItemEditRelationship.setText(bundle.getString("jMenuItemEditRelationship")); // NOI18N
        jMenuItemEditRelationship.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemEditRelationshipActionPerformed(evt);
            }
        });
        jMenuRelationshipCheats.add(jMenuItemEditRelationship);

        jMenuItemClearAllRelationships.setText(bundle.getString("jMenuItemClearAllRelationships")); // NOI18N
        jMenuRelationshipCheats.add(jMenuItemClearAllRelationships);

        jMenuItemIntroduceSelfToEveryone.setText(bundle.getString("jMenuItemIntroduceSelfToEveryone")); // NOI18N
        jMenuRelationshipCheats.add(jMenuItemIntroduceSelfToEveryone);

        jMenuCheats.add(jMenuRelationshipCheats);

        jMenuBarMain.add(jMenuCheats);

        jMenuWindow.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jMenuWindowMnemonic").charAt(0));
        jMenuWindow.setText(bundle.getString("jMenuWindow")); // NOI18N

        jMenuItemCascade.setText(bundle.getString("jMenuItemCascade")); // NOI18N
        jMenuItemCascade.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemCascadeActionPerformed(evt);
            }
        });
        jMenuWindow.add(jMenuItemCascade);
        jMenuWindow.add(jSeparator5);

        jMenuCurrentWindows.setText(bundle.getString("jMenuCurrentWindows")); // NOI18N

        jMenuItemNoWindows.setText(bundle.getString("jMenuItemNoWindows")); // NOI18N
        jMenuItemNoWindows.setEnabled(false);
        jMenuCurrentWindows.add(jMenuItemNoWindows);

        jMenuWindow.add(jMenuCurrentWindows);
        jMenuWindow.add(jSeparator6);

        jMenuItemClose.setText(bundle.getString("jMenuItemClose")); // NOI18N
        jMenuItemClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemCloseActionPerformed(evt);
            }
        });
        jMenuWindow.add(jMenuItemClose);

        jMenuItemCloseAll.setText(bundle.getString("jMenuItemCloseAll")); // NOI18N
        jMenuItemCloseAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemCloseAllActionPerformed(evt);
            }
        });
        jMenuWindow.add(jMenuItemCloseAll);

        jMenuBarMain.add(jMenuWindow);

        jMenuHelp.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jMenuHelpMnemonic").charAt(0));
        jMenuHelp.setText(bundle.getString("jMenuHelp")); // NOI18N

        jMenuItemAbout.setMnemonic(java.util.ResourceBundle.getBundle("main/ConsoleGUI").getString("jMenuItemAboutMnemonic").charAt(0));
        jMenuItemAbout.setText(bundle.getString("jMenuItemAbout")); // NOI18N
        jMenuItemAbout.setName("jMenuItemAbout"); // NOI18N
        jMenuItemAbout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jMenuItemAboutActionPerformed(evt);
            }
        });
        jMenuHelp.add(jMenuItemAbout);

        jMenuBarMain.add(jMenuHelp);

        setJMenuBar(jMenuBarMain);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jToolBarCheatsBar, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jToolBarStatusBar, javax.swing.GroupLayout.DEFAULT_SIZE, 434, Short.MAX_VALUE)
            .addComponent(jDesktopPaneDesktop, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jToolBarCheatsBar, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jDesktopPaneDesktop, javax.swing.GroupLayout.DEFAULT_SIZE, 318, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jToolBarStatusBar, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private class ExecuteCommand implements Runnable {
        private final String command;
        private final boolean isOnOff;
        private final boolean value;
        
        public ExecuteCommand(String command, boolean isOnOff, boolean value) {
            this.command = command;
            this.isOnOff = isOnOff;
            this.value = value;
        }
        
        @Override
        public void run() {
            ConsoleGUI.this.executeCommand(String.format(command + " %s",
                    isOnOff ? (value ? "on" : "off") : (value ? "true" : "false")));
        }
        
    }
    
    private void toggleMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        var isOnOff = false;
        final var value = ((javax.swing.JCheckBoxMenuItem) evt.getSource()).getState();
        var command = "";
        
        switch (evt.getActionCommand()) {
            case "Testing Cheats": {
                command = "|testingcheats";
                isOnOff = false;
                break;
            }
            case "Hover Effects": {
                command = "hovereffects";
                isOnOff = true;
                break;
            }
            case "Headline Effects": {
                command = "headlineeffects";
                isOnOff = true;
                break;
            }
            case "Move Objects": {
                command = "bb.moveobjects";
                isOnOff = true;
                break;
            }
            case "Hidden Objects": {
                command = "bb.showhiddenobjects";
                isOnOff = false;
                break;
            }
            case "Game-Play Unlocks": {
                command = "bb.ignoregameplayunlocksentitlement";
                isOnOff = false;
                break;
            }
            case "Free Real Estate": {
                command = "freerealestate";
                isOnOff = true;
                break;
            }
            case "Full-Edit Mode": {
                command = "cas.fulleditmode";
                isOnOff = true;
                break;
            }
        }
        
        new Thread(new ExecuteCommand(command, isOnOff, value)).start();
    }
    
    private void jMenuItemExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemExitActionPerformed
        this.closeWindow();
    }//GEN-LAST:event_jMenuItemExitActionPerformed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if (this.jInternalFrameSettings.isVisible()) {
            this.closeSettingsWindow();

            if (this.jInternalFrameSettings.isVisible()) {
                return;
            }
        }
        this.closeWindow();
    }//GEN-LAST:event_formWindowClosing

    private void jCheckBoxMenuItemCheatsConsoleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxMenuItemCheatsConsoleActionPerformed
        this.updateConsoleCheatState(evt);
    }//GEN-LAST:event_jCheckBoxMenuItemCheatsConsoleActionPerformed

    private void jToggleButtonCheatsConsoleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButtonCheatsConsoleActionPerformed
        this.updateConsoleCheatState(evt);
    }//GEN-LAST:event_jToggleButtonCheatsConsoleActionPerformed

    private void jInternalFrameConsoleInternalFrameClosing(javax.swing.event.InternalFrameEvent evt) {//GEN-FIRST:event_jInternalFrameConsoleInternalFrameClosing
        this.updateConsoleCheatState(evt);
    }//GEN-LAST:event_jInternalFrameConsoleInternalFrameClosing

    private void jToggleButtonCheatsExplorerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jToggleButtonCheatsExplorerActionPerformed
        this.updateCheatsExplorerState(evt);
    }//GEN-LAST:event_jToggleButtonCheatsExplorerActionPerformed

    private void jCheckBoxMenuItemCheatsExplorerActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxMenuItemCheatsExplorerActionPerformed
        this.updateCheatsExplorerState(evt);
    }//GEN-LAST:event_jCheckBoxMenuItemCheatsExplorerActionPerformed

    private void jInternalFrameCheatsExplorerInternalFrameClosing(javax.swing.event.InternalFrameEvent evt) {//GEN-FIRST:event_jInternalFrameCheatsExplorerInternalFrameClosing
        this.updateCheatsExplorerState(evt);
    }//GEN-LAST:event_jInternalFrameCheatsExplorerInternalFrameClosing

    private void jMenuItemOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemOptionsActionPerformed
        this.openSettingsWindow();
    }//GEN-LAST:event_jMenuItemOptionsActionPerformed

    private void jButtonSettingsChooseColourActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSettingsChooseColourActionPerformed
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        var category = this.jComboBoxSettingsEditorColourCategory.getSelectedItem().toString();
        var colour = this.jLabelSettingsColourPreview.getBackground();

        var choice
                = javax.swing.JColorChooser.showDialog(this, bundle.getString("colourChooserTitle"), colour);
        if (choice != null) {
            this.jFormattedTextFieldSettingsEditorColour.setText(
                    ConsoleGUI.getHexStringForColour(choice));
            this.jLabelSettingsColourPreview.setBackground(choice);

            this.currentSettings.colours.put(category, choice);
            this.updatePreview();
        }
    }//GEN-LAST:event_jButtonSettingsChooseColourActionPerformed

    private void jComboBoxSettingsEditorColourCategoryActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxSettingsEditorColourCategoryActionPerformed
        var category = this.jComboBoxSettingsEditorColourCategory.getSelectedItem().toString();
        var colour = this.currentSettings.colours.get(category);

        this.jFormattedTextFieldSettingsEditorColour.setText(
                ConsoleGUI.getHexStringForColour(colour));
        this.jLabelSettingsColourPreview.setBackground(colour);
    }//GEN-LAST:event_jComboBoxSettingsEditorColourCategoryActionPerformed

    private void jComboBoxSettingsFontFamilyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxSettingsFontFamilyActionPerformed
        this.currentSettings.setFont(this.jComboBoxSettingsFontFamily.getSelectedItem().toString(),
                ((Integer) this.jComboBoxSettingsFontSize.getSelectedItem()).floatValue());
        this.updatePreview();
    }//GEN-LAST:event_jComboBoxSettingsFontFamilyActionPerformed

    private void jComboBoxSettingsFontSizeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxSettingsFontSizeActionPerformed
        this.currentSettings.setFont(this.jComboBoxSettingsFontFamily.getSelectedItem().toString(),
                ((Integer) this.jComboBoxSettingsFontSize.getSelectedItem()).floatValue());
        this.updatePreview();
    }//GEN-LAST:event_jComboBoxSettingsFontSizeActionPerformed

    private void jComboBoxSettingsLookAndFeelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxSettingsLookAndFeelActionPerformed
        var laf = this.jComboBoxSettingsLookAndFeel.getSelectedItem().toString();

        this.currentSettings.lookAndFeel(laf.equals("System") ? "[System]" : laf);
        this.determineIfSettingsChanged();
    }//GEN-LAST:event_jComboBoxSettingsLookAndFeelActionPerformed

    private void jButtonSettingsOKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSettingsOKActionPerformed
        if (!ConsoleGUI.settings.equals(this.currentSettings)) {
            this.saveSettings();
        }
        this.closeSettingsWindow();
    }//GEN-LAST:event_jButtonSettingsOKActionPerformed

    private void jButtonSettingsApplyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSettingsApplyActionPerformed
        this.saveSettings();
    }//GEN-LAST:event_jButtonSettingsApplyActionPerformed

    private void jButtonSettingsCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSettingsCancelActionPerformed
        this.closeSettingsWindow();
    }//GEN-LAST:event_jButtonSettingsCancelActionPerformed

    private void jInternalFrameSettingsInternalFrameClosing(javax.swing.event.InternalFrameEvent evt) {//GEN-FIRST:event_jInternalFrameSettingsInternalFrameClosing
        this.closeSettingsWindow();
    }//GEN-LAST:event_jInternalFrameSettingsInternalFrameClosing

    private void openSettingsWindow() {
        this.jInternalFrameSettings.setLocation(30, 30);
        this.jInternalFrameSettings.pack();
        this.jTabbedPaneSettings.setSelectedIndex(0);
        this.jInternalFrameSettings.setVisible(true);

        this.jComboBoxSettingsMenuItem.setSelectedIndex(1);

        this.jButtonSettingsOK.requestFocus();
        this.updatePreview();

        this.jButtonSettingsApply.setEnabled(false);
        this.jButtonSettingsOK.setEnabled(true);
        this.currentSettings.isInValidState = true;
    }

    private void jFormattedTextFieldSettingsEditorColourKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jFormattedTextFieldSettingsEditorColourKeyReleased
        var text = this.jFormattedTextFieldSettingsEditorColour.getText();
        java.util.function.Supplier<Void> onError = () -> {
            this.jFormattedTextFieldSettingsEditorColour.setBackground(
                    new java.awt.Color(255, 128, 128));
            this.currentSettings.isInValidState = false;
            return null;
        };

        if (text.length() != 3 && text.length() != 6) {
            onError.get();
        } else {
            try {
                int value;
                if (text.length() == 6) {
                    value = Integer.parseInt(text, 16);
                } else {
                    value = Integer.parseInt(text, 16);

                    var blue = (value & 0x00f);
                    var green = (value & 0x0f0) >> 4;
                    var red = (value & 0xf00) >> 8;

                    value = (red << 20) | (red << 16) | (green << 12) | (green << 8) | (blue << 4) | blue;
                }

                var minValue = 0;
                var maxValue = 2 << (8 * 3) - 1;

                if (!(minValue <= value && value <= maxValue)) {
                    onError.get();
                } else {
                    var colour = new java.awt.Color(value);

                    this.jFormattedTextFieldSettingsEditorColour.setBackground(java.awt.Color.WHITE);
                    this.jLabelSettingsColourPreview.setBackground(colour);

                    var category = this.jComboBoxSettingsEditorColourCategory.getSelectedItem().toString();
                    this.currentSettings.colours.put(category, colour);
                    this.currentSettings.isInValidState = true;
                    this.updatePreview();
                }
            } catch (NumberFormatException ex) {
                onError.get();
            }
        }
    }//GEN-LAST:event_jFormattedTextFieldSettingsEditorColourKeyReleased

    private void jCheckBoxSettingsOpenAtReadyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxSettingsOpenAtReadyActionPerformed
        this.currentSettings.openAtReady(this.jCheckBoxSettingsOpenAtReady.isSelected());
        this.determineIfSettingsChanged();
    }//GEN-LAST:event_jCheckBoxSettingsOpenAtReadyActionPerformed

    private void jTextFieldConsolePromptKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextFieldConsolePromptKeyReleased
        switch (evt.getKeyCode()) {
            case java.awt.event.KeyEvent.VK_ENTER: {
                var command = this.jTextFieldConsolePrompt.getText();
                this.jTextFieldConsolePrompt.setText("");

                new Thread(() -> {
                    this.jTextFieldConsolePrompt.setEnabled(false);

                    var result = this.executeCommand(command);
                    if (result != null)
                        this.history.addValue(command, result);
                    else
                        this.history.addValue(command, "OUTPUT: TIMED-OUT");
                    
                    this.jEditorPaneConsoleOutput.setText(this.history.toString());

                    this.history.resetPosition(true);
                    this.jTextFieldConsolePrompt.setEnabled(true);
                }).start();
                break;
            }
            case java.awt.event.KeyEvent.VK_UP: {
                var value = this.history.decrementPosition();
                if (value.isPresent()) {
                    this.jTextFieldConsolePrompt.setText(value.get());
                }

                break;
            }
            case java.awt.event.KeyEvent.VK_DOWN: {
                var value = this.history.incrementPosition();
                if (value.isPresent()) {
                    this.jTextFieldConsolePrompt.setText(value.get());
                } else {
                    this.jTextFieldConsolePrompt.setText("");
                }

                break;
            }
        }
    }//GEN-LAST:event_jTextFieldConsolePromptKeyReleased

    private void jCheckBoxSettingsSaveWindowSizeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxSettingsSaveWindowSizeActionPerformed
        var selected = this.jCheckBoxSettingsSaveWindowSize.isSelected();
        this.currentSettings.savePreviousDimension = selected;
        this.currentSettings.isInValidState = true;
        this.determineIfSettingsChanged();

        this.jTextFieldSettingsWindowHeight.setEnabled(!selected);
        this.jTextFieldSettingsWindowWidth.setEnabled(!selected);
    }//GEN-LAST:event_jCheckBoxSettingsSaveWindowSizeActionPerformed

    private void jTextFieldSettingsWindowHeightKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextFieldSettingsWindowHeightKeyReleased
        int minValue = Settings.MINIMUM_SIZE.height;
        int maxValue = this.getMaximumSize().height;
        java.util.function.Supplier<Void> onError = () -> {
            this.jTextFieldSettingsWindowHeight.setBackground(
                    new java.awt.Color(255, 128, 128));
            return null;
        };

        try {
            int value = Integer.parseInt(this.jTextFieldSettingsWindowHeight.getText());
            this.currentSettings.defaultDimension.height = value;
            var isValid = value >= minValue && value <= maxValue;

            if (!isValid) {
                onError.get();
            } else {
                this.jTextFieldSettingsWindowHeight.setBackground(java.awt.Color.WHITE);
            }
            this.currentSettings.isInValidState = isValid;
        } catch (NumberFormatException ex) {
            onError.get();
            this.currentSettings.isInValidState = false;
        }

        this.determineIfSettingsChanged();
    }//GEN-LAST:event_jTextFieldSettingsWindowHeightKeyReleased

    private void jTextFieldSettingsWindowWidthKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextFieldSettingsWindowWidthKeyReleased
        int minValue = Settings.MINIMUM_SIZE.width;
        int maxValue = this.getMaximumSize().width;
        java.util.function.Supplier<Void> onError = () -> {
            this.jTextFieldSettingsWindowWidth.setBackground(
                    new java.awt.Color(255, 128, 128));
            return null;
        };

        try {
            int value = Integer.parseInt(this.jTextFieldSettingsWindowWidth.getText());
            this.currentSettings.defaultDimension.width = value;
            var isValid = value >= minValue && value <= maxValue;

            if (!isValid) {
                onError.get();
            } else {
                this.jTextFieldSettingsWindowWidth.setBackground(java.awt.Color.WHITE);
            }
            this.currentSettings.isInValidState = isValid;
        } catch (NumberFormatException ex) {
            onError.get();
            this.currentSettings.isInValidState = false;
        }

        this.determineIfSettingsChanged();
    }//GEN-LAST:event_jTextFieldSettingsWindowWidthKeyReleased

    private void jMenuItemAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemAboutActionPerformed
        this.jDialogAbout.setVisible(true);
        this.jButtonAboutClose.requestFocus();
    }//GEN-LAST:event_jMenuItemAboutActionPerformed

    private void jButtonAboutCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonAboutCloseActionPerformed
        this.jDialogAbout.setVisible(false);
    }//GEN-LAST:event_jButtonAboutCloseActionPerformed

    public static String getKeyStrokeText(KeyBinding binding) {
        var modifiers = binding.getKeyStroke().getModifiers();
        var modifiersText = String.format("%s", java.awt.event.KeyEvent.getModifiersExText(modifiers));
        var keyText = java.awt.event.KeyEvent.getKeyText(binding.keyCode);

        return String.format("%s%s",
                modifiers == 0 ? "" : String.format("%s+", modifiersText),
                keyText);
    }

    private void jComboBoxSettingsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {
        if (this.currentSettings == null) {
            return;
        }

        var selectedItem = (MenuItem) this.jComboBoxSettingsMenuItem.getSelectedItem();
        var keyBinding = this.currentSettings.keyBindings.get(selectedItem.name);
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");

        if (keyBinding != null) {
            this.jCheckBoxSettingsAlt.setSelected(keyBinding.altMask);
            this.jCheckBoxSettingsCtrl.setSelected(keyBinding.ctrlMask);
            this.jCheckBoxSettingsShift.setSelected(keyBinding.shiftMask);

            this.jTextFieldKeyStroke.setText(ConsoleGUI.getKeyStrokeText(keyBinding));

            this.jComboBoxSettingsShortcutKey.setSelectedItem(
                    this.virtualKeys.get(keyBinding.keyCode));
        } else {
            this.jCheckBoxSettingsAlt.setSelected(false);
            this.jCheckBoxSettingsCtrl.setSelected(false);
            this.jCheckBoxSettingsShift.setSelected(false);

            this.jComboBoxSettingsShortcutKey.setSelectedItem(
                    this.virtualKeys.get(-1));
            
            this.jTextFieldKeyStroke.setText(bundle.getString("jComboBoxSettingsShortcutKeyNone"));
        }
    }

    private boolean isValidKeyBinding(String key, KeyBinding binding) {
        var keys = this.currentSettings.keyBindings.entrySet()
              .stream()
              .filter(entry -> java.util.Objects.equals(entry.getValue(), binding))
              .map(java.util.Map.Entry::getKey)
              .collect(java.util.stream.Collectors.toList());
        
        return keys.isEmpty() ||
                    (keys.size() == 1 && keys.get(0).equals(key));
    }
    
    private void putKeyBinding(String key, KeyBinding binding) {
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        
        if (this.currentSettings.keyBindings.containsValue(binding) && 
                    !this.isValidKeyBinding(key, binding)) {
                this.currentSettings.isInValidState = false;
                this.jTextFieldKeyStroke.setBackground(new java.awt.Color(255, 128, 128));
                this.jTextFieldKeyStroke.setToolTipText(bundle.getString("InavlidKeyBinding"));
            }
            else {
                this.currentSettings.isInValidState = true;
                this.jTextFieldKeyStroke.setBackground(java.awt.Color.WHITE);
                this.currentSettings.keyBindings.put(key, binding);
                this.jTextFieldKeyStroke.setToolTipText("");
            }
    }
    
    private void updateKeyBinding() {
        var key = ((VirtualKey) this.jComboBoxSettingsShortcutKey.getSelectedItem()).keyCode;
        var menu = ((MenuItem) this.jComboBoxSettingsMenuItem.getSelectedItem());

        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        if (key != -1) {
            var binding = new KeyBinding(key, false, false, false);
            binding.altMask = this.jCheckBoxSettingsAlt.isSelected();
            binding.ctrlMask = this.jCheckBoxSettingsCtrl.isSelected();
            binding.shiftMask = this.jCheckBoxSettingsShift.isSelected();
            
            this.putKeyBinding(menu.name, binding);

            this.jTextFieldKeyStroke.setText(ConsoleGUI.getKeyStrokeText(binding));
        } else {
            this.jTextFieldKeyStroke.setText(bundle.getString("jComboBoxSettingsShortcutKeyNone"));
            this.jCheckBoxSettingsAlt.setSelected(false);
            this.jCheckBoxSettingsCtrl.setSelected(false);
            this.jCheckBoxSettingsShift.setSelected(false);
            this.currentSettings.keyBindings.put(menu.name, new KeyBinding());
        }

        this.determineIfSettingsChanged();
    }

    private void jComboBoxSettingsShortcutKeyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxSettingsShortcutKeyActionPerformed
        this.updateKeyBinding();
    }//GEN-LAST:event_jComboBoxSettingsShortcutKeyActionPerformed

    private void jCheckBoxSettingsCtrlActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxSettingsCtrlActionPerformed
        this.updateKeyBinding();
    }//GEN-LAST:event_jCheckBoxSettingsCtrlActionPerformed

    private void jCheckBoxSettingsShiftActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxSettingsShiftActionPerformed
        this.updateKeyBinding();
    }//GEN-LAST:event_jCheckBoxSettingsShiftActionPerformed

    private void jCheckBoxSettingsAltActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxSettingsAltActionPerformed
        this.updateKeyBinding();
    }//GEN-LAST:event_jCheckBoxSettingsAltActionPerformed

    private void jButtonSettingsExportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSettingsExportActionPerformed
        var fileChooser = new javax.swing.JFileChooser();
        var fileFilter = new javax.swing.filechooser.FileNameExtensionFilter("Settings file", "ser");
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");

        fileChooser.addChoosableFileFilter(fileFilter);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setDialogTitle(bundle.getString("ExportFileDialogTitle"));
        fileChooser.setMultiSelectionEnabled(false);

        if (fileChooser.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            var file = fileChooser.getSelectedFile();
            if (!file.getName().endsWith(".ser")) {
                file = new java.io.File(file.getPath() + ".ser");
            }

            if (file.exists()) {
                var choice = javax.swing.JOptionPane.showInternalConfirmDialog(
                        fileChooser,
                        String.format(bundle.getString("ExportFileDialogOverwriteMessage"),
                                file.getName()),
                        bundle.getString("ExportFileDialogOverwriteTitle"),
                        javax.swing.JOptionPane.WARNING_MESSAGE,
                        javax.swing.JOptionPane.YES_NO_CANCEL_OPTION);
                if (choice != javax.swing.JOptionPane.YES_OPTION) {
                    return;
                }
            }

            this.saveSettings(file, false);
        }
    }//GEN-LAST:event_jButtonSettingsExportActionPerformed

    private void jButtonSettingsImportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonSettingsImportActionPerformed
        var fileChooser = new javax.swing.JFileChooser();
        var fileFilter = new javax.swing.filechooser.FileNameExtensionFilter("Settings file", "ser");
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");

        fileChooser.addChoosableFileFilter(fileFilter);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setDialogTitle(bundle.getString("ImportFileDialogTitle"));
        fileChooser.setMultiSelectionEnabled(false);

        if (fileChooser.showOpenDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
            var file = fileChooser.getSelectedFile();

            try {
                Settings previousSettings = (Settings) ConsoleGUI.settings.clone();

                if (ConsoleGUI.readSettings(file)) {
                    try {
                        this.currentSettings = (Settings) ConsoleGUI.settings.clone();
                    } catch (CloneNotSupportedException ex) {
                    }

                    javax.swing.JOptionPane.showMessageDialog(this,
                            bundle.getString("ImportFileDialogSuccessMessage"),
                            bundle.getString("ImportFileDialogSuccessTitle"),
                            javax.swing.JOptionPane.INFORMATION_MESSAGE);
                } else {
                    javax.swing.JOptionPane.showMessageDialog(this,
                            bundle.getString("ImportFileDialogFailureMessage"),
                            bundle.getString("ImportFileDialogFailureTitle"),
                            javax.swing.JOptionPane.ERROR_MESSAGE);

                    try {
                        ConsoleGUI.settings = (Settings) previousSettings.clone();
                        ConsoleGUI.settings = (Settings) ConsoleGUI.settings.clone();
                        this.currentSettings = (Settings) ConsoleGUI.settings.clone();
                    } catch (CloneNotSupportedException ex) {
                        System.out.println(ex);
                    }
                }

            } catch (CloneNotSupportedException ex) {
            }

            this.currentSettings.isInValidState = true;
            this.implementSettings();
            this.saveSettings(true);
        }
    }//GEN-LAST:event_jButtonSettingsImportActionPerformed

    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        var dimension = this.getSize();

        if (!this.jInternalFrameSettings.isVisible()) {
            return;
        }

        this.jTextFieldSettingsWindowHeight.setText(String.valueOf((int) dimension.getHeight()));
        this.jTextFieldSettingsWindowWidth.setText(String.valueOf((int) dimension.getWidth()));

        this.currentSettings.defaultDimension(dimension);
        this.determineIfSettingsChanged();
    }//GEN-LAST:event_formComponentResized

    private void jMenuItemCascadeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemCascadeActionPerformed
        var manager = this.jDesktopPaneDesktop.getDesktopManager();
        int separation = 24;
        int offset = 0;

        for (var frame : this.jDesktopPaneDesktop.getAllFrames()) {
            if (!frame.isVisible()) {
                continue;
            }

            manager.activateFrame(frame);
            manager.setBoundsForFrame(frame, offset, offset, frame.getWidth(), frame.getHeight());

            offset += separation;
        }
    }//GEN-LAST:event_jMenuItemCascadeActionPerformed

    private void jMenuItemCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemCloseActionPerformed
        var window = this.jDesktopPaneDesktop.getSelectedFrame();

        if (window != null)
            window.doDefaultCloseAction();
    }//GEN-LAST:event_jMenuItemCloseActionPerformed

    private void jMenuItemCloseAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemCloseAllActionPerformed
        for (var frame : this.jDesktopPaneDesktop.getAllFrames()) {
            if (!frame.isVisible()) {
                continue;
            }

            frame.doDefaultCloseAction();
        }
    }//GEN-LAST:event_jMenuItemCloseAllActionPerformed

    private void jDesktopPaneDesktopComponentAdded(java.awt.event.ContainerEvent evt) {//GEN-FIRST:event_jDesktopPaneDesktopComponentAdded
        var component = evt.getChild();

        if (component != null && component instanceof javax.swing.JInternalFrame) {
            final var frame = (javax.swing.JInternalFrame) component;
            final var menu = this.jMenuCurrentWindows;
            final var manager = this.jDesktopPaneDesktop.getDesktopManager();

            this.jMenuItemNoWindows.setVisible(false);

            var i = menu.getMenuComponentCount();
            this.windowMap.put(frame, i);

            var menuItem = new javax.swing.JMenuItem(frame.getTitle());
            menuItem.setEnabled(false);
            menuItem.addActionListener((var listener) -> manager.activateFrame(frame));
            menu.add(menuItem);

            component.addComponentListener(new java.awt.event.ComponentListener() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                }

                @Override
                public void componentMoved(java.awt.event.ComponentEvent e) {
                }

                @Override
                public void componentShown(java.awt.event.ComponentEvent e) {
                    var index = ConsoleGUI.this.windowMap.get(e.getComponent());
                    if (index != null) {
                        menu.getItem(index).setEnabled(true);
                    }
                }

                @Override
                public void componentHidden(java.awt.event.ComponentEvent e) {
                    var index = ConsoleGUI.this.windowMap.get(e.getComponent());
                    if (index != null) {
                        menu.getItem(index).setEnabled(false);
                    }
                }
            });
        }
    }//GEN-LAST:event_jDesktopPaneDesktopComponentAdded

    private void jTextFieldKeyStrokeKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextFieldKeyStrokeKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_CONTROL ||
                evt.getKeyCode() == KeyEvent.VK_ALT || 
                evt.getKeyCode() == KeyEvent.VK_SHIFT)
            return;
        
        var menu = ((MenuItem) this.jComboBoxSettingsMenuItem.getSelectedItem());
        boolean ctrl_mask = evt.isControlDown(),
                shift_mask = evt.isShiftDown(),
                alt_mask = evt.isAltDown();
        
        evt.consume();
        
        var binding = new KeyBinding(evt.getKeyCode(), ctrl_mask, shift_mask, alt_mask);
        this.putKeyBinding(menu.name, binding);
        this.determineIfSettingsChanged();
        
        this.jCheckBoxSettingsAlt.setSelected(alt_mask);
        this.jCheckBoxSettingsCtrl.setSelected(ctrl_mask);
        this.jCheckBoxSettingsShift.setSelected(shift_mask);
        this.jComboBoxSettingsShortcutKey.setSelectedItem(this.virtualKeys.get(evt.getKeyCode()));
        
        this.jButtonSettingsOK.requestFocusInWindow();
    }//GEN-LAST:event_jTextFieldKeyStrokeKeyPressed

    private void jListInitialSettingsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jListInitialSettingsMouseClicked
        var list = (javax.swing.JList<InitialSettingsItem>) evt.getSource();
        int index = list.locationToIndex(evt.getPoint());

        if (list.getModel().getSize() == 0) {
            return;
        }

        var item = (InitialSettingsItem) list.getModel().getElementAt(index);

        if (item == null) {
            return;
        }

        item.state(!item.state());
        var itemName = item.item.getName();
        this.currentSettings.initialSettings.put(itemName, item.state());
        this.determineIfSettingsChanged();

        list.repaint(list.getCellBounds(index, index));
    }//GEN-LAST:event_jListInitialSettingsMouseClicked
    
    private void jButtonMCCCActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonMCCCActionPerformed
        this.jTableMCCCSettings.setModel(new javax.swing.table.DefaultTableModel() {
            {
                var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
                this.columnIdentifiers.add(bundle.getString("jDialogMCCCSettingsTableSettingColumn"));
                this.columnIdentifiers.add(bundle.getString("jDialogMCCCSettingsTableValueColumn"));
                
                ConsoleGUI.settings.mcccSettings.forEach((var key, var value) -> {
                    var values = new java.util.Vector();
                    values.add(ConsoleGUI.this.initialSettingsHashMap.get(key).getText());
                    values.add(value);
                    this.dataVector.add(values);
                });
            }
            
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
            
            @Override
            public Class getColumnClass(int c) {
                return getValueAt(0, c).getClass();
            }
            
        });
        this.jTableMCCCSettings.setRowSorter(
                new javax.swing.table.TableRowSorter<>(this.jTableMCCCSettings.getModel()));
        
        this.jDialogMCCCSettings.pack();
        this.jDialogMCCCSettings.setVisible(true);
        this.jButtonMCCCSettingsOk.requestFocus();
    }//GEN-LAST:event_jButtonMCCCActionPerformed

    private void jButtonMCCCSettingsOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonMCCCSettingsOkActionPerformed
        this.jDialogMCCCSettings.dispose();
    }//GEN-LAST:event_jButtonMCCCSettingsOkActionPerformed

    private void jMenuItemMotherlodeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemMotherlodeActionPerformed
        new Thread(() -> this.executeCommand("motherlode")).start();
    }//GEN-LAST:event_jMenuItemMotherlodeActionPerformed

    private void jMenuItemRosebudActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemRosebudActionPerformed
        new Thread(() -> this.executeCommand("rosebud")).start();
    }//GEN-LAST:event_jMenuItemRosebudActionPerformed

    private void jMenuItemClearMoneyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemClearMoneyActionPerformed
        new Thread(() -> this.executeCommand("money 0")).start();
    }//GEN-LAST:event_jMenuItemClearMoneyActionPerformed

    private void jMenuItemEnableFreeBuildActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemEnableFreeBuildActionPerformed
        new Thread(() -> this.executeCommand("freebuild")).start();
    }//GEN-LAST:event_jMenuItemEnableFreeBuildActionPerformed

    private void jButtonTMexSettingsOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonTMexSettingsOkActionPerformed
        this.jDialogTMexSettings.dispose();
    }//GEN-LAST:event_jButtonTMexSettingsOkActionPerformed
    
    private void jButtonTMEXActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonTMEXActionPerformed
        this.jTableTMexSettings.setModel(new javax.swing.table.DefaultTableModel() {
            {
                var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
                this.columnIdentifiers.add(bundle.getString("jDialogTMexSettingsTableSettingColumn"));
                this.columnIdentifiers.add(bundle.getString("jDialogTMexSettingsTableValueColumn"));
                
                ConsoleGUI.settings.tmexSettings.forEach((var key, var value) -> {
                    var values = new java.util.Vector();
                    values.add(ConsoleGUI.this.initialSettingsHashMap.get(key).getText());
                    values.add(value);
                    this.dataVector.add(values);
                });
            }
            
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
            
            @Override
            public Class getColumnClass(int c) {
                return getValueAt(0, c).getClass();
            }
            
        });
        this.jTableTMexSettings.setRowSorter(
                new javax.swing.table.TableRowSorter<>(this.jTableTMexSettings.getModel()));
        
        this.jDialogTMexSettings.pack();
        this.jDialogTMexSettings.setVisible(true);
        this.jButtonTMexSettingsOk.requestFocus();
    }//GEN-LAST:event_jButtonTMEXActionPerformed

    private void jMenuItemSetMoneyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemSetMoneyActionPerformed
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        var useQueries = ConsoleGUI.settings.useNonEssentialQueries();
        
        int funds = 0;
        if (useQueries) {
            var response = this.executeQuery("HOUSEHOLD FUNDS");
            if (response != null)
                funds = Integer.parseInt(response.replace("MESSAGE: INFO: FUNDS: ", ""));
        }
        
        var model = (javax.swing.SpinnerNumberModel) this.jSpinnerMoneyChooser.getModel();
        model.setMaximum(9999999);
        model.setMinimum(0);
        model.setValue(funds);
        
        var description = bundle.getString("jLabelMoneyChooserDescriptionSetMoney");
        
        this.jDialogMoneyChooser.setTitle(bundle.getString("jDialogMoneyChooserSetMoneyTitle"));
        this.jLabelMoneyChooserDescription.setText(description);
        this.jDialogMoneyChooser.pack();
        this.jButtonMoneyChooserOk.setActionCommand("money %d");
        this.jDialogMoneyChooser.setVisible(true);
        this.jButtonMoneyChooserOk.requestFocus();
    }//GEN-LAST:event_jMenuItemSetMoneyActionPerformed

    private void jMenuItemDepositMoneyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemDepositMoneyActionPerformed
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        var useQueries = ConsoleGUI.settings.useNonEssentialQueries();
        
        int funds = 0;
        if (useQueries) {
            var response = this.executeQuery("HOUSEHOLD FUNDS");
            if (response != null)
                funds = Integer.parseInt(response.replace("MESSAGE: INFO: FUNDS: ", ""));
        }
        
        var model = (javax.swing.SpinnerNumberModel) this.jSpinnerMoneyChooser.getModel();
        model.setMaximum(9999999 - funds);
        model.setMinimum(1);
        model.setValue(1);
        
        var description = bundle.getString("jLabelMoneyChooserDescriptionDepositMoney");
        description = String.format(description, model.getMaximum());
        
        this.jDialogMoneyChooser.setTitle(bundle.getString("jDialogMoneyChooserDepositMoneyTitle"));
        this.jLabelMoneyChooserDescription.setText(description);
        this.jDialogMoneyChooser.pack();
        this.jButtonMoneyChooserOk.setActionCommand("sims.modify_funds %d");
        this.jDialogMoneyChooser.setVisible(true);
        this.jButtonMoneyChooserOk.requestFocus();
    }//GEN-LAST:event_jMenuItemDepositMoneyActionPerformed

    private void jMenuItemWithdrawMoneyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemWithdrawMoneyActionPerformed
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        var useQueries = ConsoleGUI.settings.useNonEssentialQueries();
        
        int funds = 0;
        if (useQueries) {
            var response = this.executeQuery("HOUSEHOLD FUNDS");
            if (response != null)
                funds = Integer.parseInt(response.replace("MESSAGE: INFO: FUNDS: ", ""));
        }
        
        var model = (javax.swing.SpinnerNumberModel) this.jSpinnerMoneyChooser.getModel();
        model.setMaximum(funds);
        model.setMinimum(1);
        model.setValue(1);
        
        var description = bundle.getString("jLabelMoneyChooserDescriptionWithdrawMoney");
        description = String.format(description, model.getMaximum());
        
        this.jDialogMoneyChooser.setTitle(bundle.getString("jDialogMoneyChooserWithdrawMoneyTitle"));
        this.jLabelMoneyChooserDescription.setText(description);
        this.jDialogMoneyChooser.pack();
        this.jButtonMoneyChooserOk.setActionCommand("sims.modify_funds -%d");
        this.jSpinnerMoneyChooser.setValue(0);
        this.jDialogMoneyChooser.setVisible(true);
        this.jButtonMoneyChooserOk.requestFocus();
    }//GEN-LAST:event_jMenuItemWithdrawMoneyActionPerformed

    private void jButtonMoneyChooserCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonMoneyChooserCancelActionPerformed
        this.jDialogMoneyChooser.dispose();
    }//GEN-LAST:event_jButtonMoneyChooserCancelActionPerformed

    private void jButtonMoneyChooserOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonMoneyChooserOkActionPerformed
        this.executeCommand(
                String.format(this.jButtonMoneyChooserOk.getActionCommand(),
                        this.jSpinnerMoneyChooser.getValue()));
        this.jDialogMoneyChooser.dispose();
    }//GEN-LAST:event_jButtonMoneyChooserOkActionPerformed

    private class SimConsumer implements Consumer<Sim> {
        
        private Species species;
        private final javax.swing.JComboBox<SimItem> parent;
        
        public SimConsumer(javax.swing.JComboBox<SimItem> parent) {
            this.species = null;
            this.parent = parent;
            
            this.parent.removeAllItems();
        }
        
        @Override
        public void accept(Sim sim) {
            if (!Objects.equals(this.species, sim.getSpecies())) {
                var category = new SimItemSeparator(sim.getSpecies().toString());
                this.parent.addItem(category);
                
                this.species = sim.getSpecies();
            }
            
            var item = new SimItem(sim);
            this.parent.addItem(item);
        }
        
    }
    
    private ArrayList<Sim> simsList = new ArrayList<>();
    private void jMenuItemEditRelationshipActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jMenuItemEditRelationshipActionPerformed
        if (this.simsList.isEmpty())
            this.simsList.addAll(this.simsMap.values());
        
        Collections.sort(this.simsList, new SimItemComparator());
        this.simsList.forEach(new SimConsumer(this.jComboBoxRelationshipMainSim));
        this.simsList.stream().filter((sim) -> !sim.equals(this.simsList.get(0)))
                .forEach(new SimConsumer(this.jComboBoxRelationshipSecondarySim));
        
        this.jComboBoxRelationshipMainSim.setSelectedIndex(1);
        this.jComboBoxRelationshipSecondarySim.setSelectedIndex(1);
        
        this.jDialogEditRelationship.pack();
        this.jButtonRelationshipOk.requestFocus();
        this.jDialogEditRelationship.setVisible(true);
    }//GEN-LAST:event_jMenuItemEditRelationshipActionPerformed

    private void populateSimsList() {
        Collections.sort(this.simsList, new SimItemComparator());
        
        if (this.jRadioButtonRelationshipFriendly.isSelected()) {
            var item = (SimItem) this.jComboBoxRelationshipMainSim.getSelectedItem();
            var index = this.jComboBoxRelationshipMainSim.getSelectedIndex();
            
            this.simsList.stream().forEach(new SimConsumer(this.jComboBoxRelationshipMainSim));
            this.simsList.stream().filter((sim) -> !sim.equals(item.getSim()))
                    .forEach(new SimConsumer(this.jComboBoxRelationshipSecondarySim));

            this.jComboBoxRelationshipMainSim.setSelectedIndex(index);
            this.jComboBoxRelationshipSecondarySim.setSelectedIndex(1);
        }
        else {
            var item = (SimItem) this.jComboBoxRelationshipMainSim.getSelectedItem();
        
            if (!item.getSim().getSpecies().equals(Species.HUMAN)) {
                int i = 1;
                var tempItem = (SimItem) this.jComboBoxRelationshipMainSim.getSelectedItem();
                while (!tempItem.getSim().getSpecies().equals(Species.HUMAN)) {
                    this.jComboBoxRelationshipMainSim.setSelectedIndex(i++);
                    tempItem = (SimItem) this.jComboBoxRelationshipMainSim.getSelectedItem();
                }
            }
            var index = this.jComboBoxRelationshipMainSim.getSelectedIndex();
            
            this.simsList.stream().filter((sim) -> sim.getSpecies().equals(Species.HUMAN))
                    .forEach(new SimConsumer(this.jComboBoxRelationshipMainSim));
            this.simsList.stream().filter((sim) -> !sim.equals(item.getSim()) &&
                    sim.getSpecies().equals(Species.HUMAN))
                    .forEach(new SimConsumer(this.jComboBoxRelationshipSecondarySim));

            this.jComboBoxRelationshipMainSim.setSelectedIndex(index);
            this.jComboBoxRelationshipSecondarySim.setSelectedIndex(1);
        }
    }
    
    private void jButtonRelationshipCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonRelationshipCancelActionPerformed
        this.jDialogEditRelationship.dispose();
    }//GEN-LAST:event_jButtonRelationshipCancelActionPerformed

    private void jButtonRelationshipOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonRelationshipOkActionPerformed
        String cheatCode = "|modifyrelationship %s %s %d %s";
        
        String sim1 = this.jComboBoxRelationshipMainSim.getSelectedItem().toString();
        String sim2 = this.jComboBoxRelationshipSecondarySim.getSelectedItem().toString();
        String type = this.jRadioButtonRelationshipFriendly.isSelected() ? "LTR_Friendship_Main" :
                "Romance_Main";
        int value = this.jSliderRelationshipRelationshipValue.getValue();
        
        String cheat1 = String.format(cheatCode, sim1, sim2, value, type);
        String cheat2 = String.format(cheatCode, sim2, sim1, value, type);
        
        new Thread(() -> {this.executeCommand(cheat1); this.executeCommand(cheat2);}).start();
        
        this.jDialogEditRelationship.dispose();
    }//GEN-LAST:event_jButtonRelationshipOkActionPerformed

    private void jRadioButtonRelationshipFriendlyActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonRelationshipFriendlyActionPerformed
        this.populateSimsList();
    }//GEN-LAST:event_jRadioButtonRelationshipFriendlyActionPerformed

    private void jRadioButtonRelationshipRomanticActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jRadioButtonRelationshipRomanticActionPerformed
        this.populateSimsList();
    }//GEN-LAST:event_jRadioButtonRelationshipRomanticActionPerformed

    private void jCheckBoxSettingsUseMaxTimeoutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxSettingsUseMaxTimeoutActionPerformed
        if (this.jCheckBoxSettingsUseMaxTimeout.isSelected()) {
            if (this.jTextFieldMaxTimeOut.getText().isEmpty()) {
                this.jTextFieldMaxTimeOut.setText(String.valueOf(Settings.DEFAULT_TIME_OUT));
            }
            this.jTextFieldMaxTimeOut.setEnabled(true);
        }
        else {
            this.jTextFieldMaxTimeOut.setEnabled(false);
            this.currentSettings.maxTimeout = -1;
        }
    }//GEN-LAST:event_jCheckBoxSettingsUseMaxTimeoutActionPerformed

    private void jTextFieldMaxTimeOutKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_jTextFieldMaxTimeOutKeyReleased
        String text = this.jTextFieldMaxTimeOut.getText();
        
        try {
            var value = Integer.parseInt(text);
            
            if (Settings.MIN_TIME_OUT <= value && value <= Settings.MAX_TIME_OUT)
                this.currentSettings.maxTimeout = value;
            else
                throw new NumberFormatException();
            
            this.jTextFieldMaxTimeOut.setBackground(java.awt.Color.WHITE);
            this.currentSettings.isInValidState = true;
        }
        catch (NumberFormatException ex) {
            this.jTextFieldMaxTimeOut.setBackground(new java.awt.Color(255, 128, 128));
            this.currentSettings.isInValidState = false;
        }
        
        this.determineIfSettingsChanged();
        if (evt.getKeyCode() != java.awt.event.KeyEvent.VK_ENTER)
            this.jTextFieldMaxTimeOut.requestFocus();
    }//GEN-LAST:event_jTextFieldMaxTimeOutKeyReleased

    private void jCheckBoxSettingsUSeNonEssentialQueriesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jCheckBoxSettingsUSeNonEssentialQueriesActionPerformed
        this.currentSettings.useNonEssentialQueries(this.jCheckBoxSettingsUSeNonEssentialQueries.isSelected());
        this.determineIfSettingsChanged();
    }//GEN-LAST:event_jCheckBoxSettingsUSeNonEssentialQueriesActionPerformed

    private void jComboBoxRelationshipMainSimActionPerformed(java.awt.event.ActionEvent evt) {                                                             
        this.populateSimsList();
    }
    
    private void jComboBoxRelationshipSecondarySimActionPerformed(java.awt.event.ActionEvent evt) {                                                             
        
    }
    
    private void jListSystemTrayMenuMouseClicked(java.awt.event.MouseEvent evt) {
        var list = (javax.swing.JList<CheckListItem>) evt.getSource();
        int index = list.locationToIndex(evt.getPoint());

        if (list.getModel().getSize() == 0) {
            return;
        }

        var item = (CheckListItem) list.getModel().getElementAt(index);

        if (item instanceof CheckListItemSeparator || item == null) {
            return;
        }

        item.state(!item.state());
        var itemName = item.item.getName();
        if (item.state()) {
            this.currentSettings.quickCheats.add(itemName);
        } else {
            this.currentSettings.quickCheats.remove(itemName);
        }
        this.determineIfSettingsChanged();

        list.repaint(list.getCellBounds(index, index));
    }
    
    private String executeQuery(String query) {
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        var executor = Executors.newSingleThreadScheduledExecutor();
        var timeout = ConsoleGUI.settings.maxTimeout;
        
        var value = executor.submit(() -> {
            var that = ConsoleGUI.this;
            
            that.connData.out.format("QUERY: %s\n", query);
            that.connData.out.flush();
            
            try {
                return that.connData.in.readLine();
            } catch (IOException ex) {
                return null;
            }
        });
        
        executor.shutdown();
        
        try {
            this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusQuerying"));
            this.jProgressBarStatus.setVisible(true);
            this.jSeparator1.setVisible(true);
            
            var result = timeout == -1 ? value.get() : value.get(timeout, TimeUnit.MILLISECONDS);
            if (result != null) {
                this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusDone"));
            }
            else {
                this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusFailed"));
            }
            
            return result;
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ConsoleGUI.class.getName()).log(Level.WARNING, null, ex);
            this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusFailed"));
        }
        catch (TimeoutException ex) {
            value.cancel(true);
            this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusTimedOut"));
        }
        finally {
            this.jProgressBarStatus.setVisible(false);
            this.jSeparator1.setVisible(false);
        }
        
        return null;
    }

    private String executeCommand(String command) {
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        var executor = Executors.newSingleThreadScheduledExecutor();
        var timeout = ConsoleGUI.settings.maxTimeout;
        
        var value = executor.submit(() -> {
            var that = ConsoleGUI.this;
            
            that.connData.out.format("COMMAND: %s\n", command);
            that.connData.out.flush();
            
            try {
                return that.connData.in.readLine();
            } catch (IOException ex) {
                return null;
            }
        });
        
        executor.shutdown();
        
        try {
            this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusExecuting"));
            this.jProgressBarStatus.setVisible(true);
            this.jSeparator1.setVisible(true);
            
            var result = timeout == -1 ? value.get() : value.get(timeout, TimeUnit.MILLISECONDS);
            if (result != null) {
                this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusDone"));
            }
            else {
                this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusFailed"));
            }
            
            return result;
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(ConsoleGUI.class.getName()).log(Level.WARNING, null, ex);
            this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusFailed"));
        }
        catch (TimeoutException ex) {
            value.cancel(true);
            javax.swing.JOptionPane.showMessageDialog(null, 
                    bundle.getString("ExecutionTimedOutMessage"),
                    bundle.getString("ExecutionTimedOutTitle"),
                    javax.swing.JOptionPane.ERROR_MESSAGE);
            this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusTimedOut"));
        }
        finally {
            this.jProgressBarStatus.setVisible(false);
            this.jSeparator1.setVisible(false);
        }
        
        return null;
    }

    private void closeSettingsWindow() {
        if (!ConsoleGUI.settings.equals(this.currentSettings)) {
            var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
            var message = bundle.getString("CloseSettingsWithoutSavingMessage");
            var title = bundle.getString("CloseSettingsWithoutSavingTitle");

            var choice = javax.swing.JOptionPane.showConfirmDialog(this, message, title,
                    javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
                    javax.swing.JOptionPane.WARNING_MESSAGE);
            this.jInternalFrameSettings.setVisible(choice != javax.swing.JOptionPane.YES_OPTION);
        } else {
            this.jInternalFrameSettings.setVisible(false);
        }

        this.createDocumentForConsole();
        this.updatePreview();
    }

    private static String getHexStringForColour(java.awt.Color colour) {
        String formatString = "%02x%02x%02x";

        int red = colour.getRed(), green = colour.getGreen(), blue = colour.getBlue();

        return String.format(formatString, red, green, blue);
    }

    private void updatePreview() {
        this.createDocumentForConsole();

        var previewHistory = new ConsoleHistory();
        previewHistory.addValue("bb.moveobjects", "OUTPUT: SUCCESS: Move objects is ON");
        previewHistory.addValue("money", "OUTPUT: FAILURE: Command missing required "
                + "parameter: amount &lt;type: int&gt;");
        previewHistory.addValue("cas.fulleditmode", "OUTPUT: NO-OP");
        previewHistory.addValue("relationships.introduce_sim_to_all_others", "OUTPUT: TIMED-OUT");

        this.jEditorPaneSettingsAppearancePreview.setText(previewHistory.toString());
        this.jEditorPaneSettingsAppearancePreview.setCaretPosition(0);

        this.determineIfSettingsChanged();
    }

    private void determineIfSettingsChanged() {
        this.jButtonSettingsApply.setEnabled(!ConsoleGUI.settings.equals(this.currentSettings));
        this.jButtonSettingsOK.setEnabled(this.currentSettings.isInValidState);

        if (this.currentSettings.isInValidState) {
            this.jButtonSettingsOK.requestFocus();
        }
    }

    private void updateConsoleCheatState(java.awt.AWTEvent evt) {
        var selected = false;

        if (evt.getSource() instanceof javax.swing.JCheckBoxMenuItem) {
            selected = ((javax.swing.JCheckBoxMenuItem) evt.getSource()).isSelected();
        } else if (evt.getSource() instanceof javax.swing.JToggleButton) {
            selected = ((javax.swing.JToggleButton) evt.getSource()).isSelected();
        } else if (evt.getSource() instanceof javax.swing.JInternalFrame) {
            selected = false;
        }

        this.jCheckBoxMenuItemCheatsConsole.setSelected(selected);
        this.jToggleButtonCheatsConsole.setSelected(selected);
        this.jInternalFrameConsole.setVisible(selected);
        this.history = new ConsoleHistory();

        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");
        this.jLabelCommandStatus.setText(bundle.getString("jLabelCommandStatusNone"));
    }

    private void updateCheatsExplorerState(java.awt.AWTEvent evt) {
        var selected = false;

        if (evt.getSource() instanceof javax.swing.JCheckBoxMenuItem) {
            selected = ((javax.swing.JCheckBoxMenuItem) evt.getSource()).isSelected();
        } else if (evt.getSource() instanceof javax.swing.JToggleButton) {
            selected = ((javax.swing.JToggleButton) evt.getSource()).isSelected();
        } else if (evt.getSource() instanceof javax.swing.JInternalFrame) {
            selected = false;
        }

        this.jCheckBoxMenuItemCheatsExplorer.setSelected(selected);
        this.jToggleButtonCheatsExplorer.setSelected(selected);
        this.jInternalFrameCheatsExplorer.setVisible(selected);
    }

    private void closeWindow() {
        var bundle = java.util.ResourceBundle.getBundle("ConsoleGUI");

        var choice = javax.swing.JOptionPane.showConfirmDialog(this,
                bundle.getString("formWindowClosingConfirm"),
                bundle.getString("formWindowClosingTitle"),
                javax.swing.JOptionPane.YES_NO_OPTION);
        if (choice != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }

        if (this.connData != null) {
            this.connData.out.print("MESSAGE: Closing\n");
            this.connData.out.flush();
        }

        if (ConsoleGUI.settings.savePreviousDimension) {
            this.currentSettings.defaultDimension = this.getSize();
            this.saveSettings(true);
        }

        System.exit(0);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        ConsoleGUI.readSettings();
        String lookAndFeel = ConsoleGUI.settings.lookAndFeel();

        try {
            if (lookAndFeel.equals("[System]")) {
                javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
            } else {
                for (var laf : javax.swing.UIManager.getInstalledLookAndFeels()) {
                    if (laf.getName().equals(lookAndFeel)) {
                        javax.swing.UIManager.setLookAndFeel(laf.getClassName());
                        break;
                    }
                }
            }
        } catch (ClassNotFoundException
                | InstantiationException
                | IllegalAccessException
                | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(ConsoleGUI.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> {
            var input = java.util.Arrays.asList(args).toString().replace("[", "").replace("]", "");
            var scanner = new Scanner(input);

            int port = DEFAULT_PORT;

            while (scanner.hasNext()) {
                if (scanner.hasNext("-debug")) {
                    scanner.next("-debug");
                    DEBUG_MODE = true;
                } else if (scanner.hasNext("-port") && scanner.hasNextInt()) {
                    scanner.next("-port");
                    port = scanner.nextInt();
                } else {
                    System.err.println("Invalid commandline arguments");
                    System.exit(1);
                }
            }

            new ConsoleGUI(port).setVisible(true);
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroupRelationship;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler10;
    private javax.swing.Box.Filler filler2;
    private javax.swing.Box.Filler filler3;
    private javax.swing.Box.Filler filler4;
    private javax.swing.Box.Filler filler5;
    private javax.swing.Box.Filler filler6;
    private javax.swing.Box.Filler filler9;
    private javax.swing.JButton jButtonAboutClose;
    private javax.swing.JButton jButtonMCCC;
    private javax.swing.JButton jButtonMCCCSettingsOk;
    private javax.swing.JButton jButtonMoneyChooserCancel;
    private javax.swing.JButton jButtonMoneyChooserOk;
    private javax.swing.JButton jButtonRelationshipCancel;
    private javax.swing.JButton jButtonRelationshipOk;
    private javax.swing.JButton jButtonSettingsApply;
    private javax.swing.JButton jButtonSettingsCancel;
    private javax.swing.JButton jButtonSettingsChooseColour;
    private javax.swing.JButton jButtonSettingsExport;
    private javax.swing.JButton jButtonSettingsImport;
    private javax.swing.JButton jButtonSettingsOK;
    private javax.swing.JButton jButtonTMEX;
    private javax.swing.JButton jButtonTMexSettingsOk;
    private javax.swing.JCheckBox jCheckBoxMCCC;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItemCheatsConsole;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItemCheatsExplorer;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItemFreeRealEstate;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItemFullEditMode;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItemGamePlayUnlocks;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItemHeadlineEffects;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItemHiddenObjects;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItemHoverEffects;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItemMoveObjects;
    private javax.swing.JCheckBoxMenuItem jCheckBoxMenuItemTestingCheats;
    private javax.swing.JCheckBox jCheckBoxSettingsAlt;
    private javax.swing.JCheckBox jCheckBoxSettingsCtrl;
    private javax.swing.JCheckBox jCheckBoxSettingsOpenAtReady;
    private javax.swing.JCheckBox jCheckBoxSettingsSaveWindowSize;
    private javax.swing.JCheckBox jCheckBoxSettingsShift;
    private javax.swing.JCheckBox jCheckBoxSettingsUSeNonEssentialQueries;
    private javax.swing.JCheckBox jCheckBoxSettingsUseMaxTimeout;
    private javax.swing.JCheckBox jCheckBoxTMEX;
    private javax.swing.JComboBox<SimItem> jComboBoxRelationshipMainSim;
    private javax.swing.JComboBox<SimItem> jComboBoxRelationshipSecondarySim;
    private javax.swing.JComboBox<String> jComboBoxSettingsEditorColourCategory;
    private javax.swing.JComboBox<String> jComboBoxSettingsFontFamily;
    private javax.swing.JComboBox<Integer> jComboBoxSettingsFontSize;
    private javax.swing.JComboBox<String> jComboBoxSettingsLookAndFeel;
    private javax.swing.JComboBox<MenuItem> jComboBoxSettingsMenuItem;
    private javax.swing.JComboBox<VirtualKey> jComboBoxSettingsShortcutKey;
    private javax.swing.JDesktopPane jDesktopPaneDesktop;
    private javax.swing.JDialog jDialogAbout;
    private javax.swing.JDialog jDialogEditRelationship;
    private javax.swing.JDialog jDialogMCCCSettings;
    private javax.swing.JDialog jDialogMoneyChooser;
    private javax.swing.JDialog jDialogTMexSettings;
    private javax.swing.JEditorPane jEditorPaneAbout;
    private javax.swing.JEditorPane jEditorPaneConsoleOutput;
    private javax.swing.JEditorPane jEditorPaneSettingsAppearancePreview;
    private javax.swing.JFormattedTextField jFormattedTextFieldSettingsEditorColour;
    private javax.swing.JInternalFrame jInternalFrameCheatsExplorer;
    private javax.swing.JInternalFrame jInternalFrameConsole;
    private javax.swing.JInternalFrame jInternalFrameSettings;
    private javax.swing.JLabel jLabelColourHexHash;
    private javax.swing.JLabel jLabelCommandStatus;
    private javax.swing.JLabel jLabelConnectionStatus;
    private javax.swing.JLabel jLabelConsolePrompt;
    private javax.swing.JLabel jLabelMoneyChooserAmount;
    private javax.swing.JLabel jLabelMoneyChooserDescription;
    private javax.swing.JLabel jLabelRelationshipMainSim;
    private javax.swing.JLabel jLabelRelationshipSecondarySim;
    private javax.swing.JLabel jLabelSettingsColourPreview;
    private javax.swing.JLabel jLabelSettingsEditorColour;
    private javax.swing.JLabel jLabelSettingsEditorColourCategory;
    private javax.swing.JLabel jLabelSettingsFontFamily;
    private javax.swing.JLabel jLabelSettingsFontSize;
    private javax.swing.JLabel jLabelSettingsKey;
    private javax.swing.JLabel jLabelSettingsLookAndFeel;
    private javax.swing.JLabel jLabelSettingsMenuItem;
    private javax.swing.JLabel jLabelSettingsMenuItemKeyStroke;
    private javax.swing.JLabel jLabelSettingsPreview;
    private javax.swing.JLabel jLabelSettingsWindowHeight;
    private javax.swing.JLabel jLabelSettingsWindowWidth;
    private javax.swing.JLabel jLabelSystemTrayMenu;
    private javax.swing.JList<InitialSettingsItem> jListInitialSettings;
    private javax.swing.JList<CheckListItem> jListSystemTrayMenu;
    private javax.swing.JMenuBar jMenuBarMain;
    private javax.swing.JMenu jMenuBuildBuy;
    private javax.swing.JMenu jMenuCareerCheats;
    private javax.swing.JMenu jMenuCheats;
    private javax.swing.JMenu jMenuCurrentWindows;
    private javax.swing.JMenu jMenuFile;
    private javax.swing.JMenu jMenuHelp;
    private javax.swing.JMenuItem jMenuItemAbout;
    private javax.swing.JMenuItem jMenuItemCascade;
    private javax.swing.JMenuItem jMenuItemClearAllRelationships;
    private javax.swing.JMenuItem jMenuItemClearAllSkills;
    private javax.swing.JMenuItem jMenuItemClearMoney;
    private javax.swing.JMenuItem jMenuItemClose;
    private javax.swing.JMenuItem jMenuItemCloseAll;
    private javax.swing.JMenuItem jMenuItemDemote;
    private javax.swing.JMenuItem jMenuItemDepositMoney;
    private javax.swing.JMenuItem jMenuItemEditRelationship;
    private javax.swing.JMenuItem jMenuItemEnableFreeBuild;
    private javax.swing.JMenuItem jMenuItemExit;
    private javax.swing.JMenuItem jMenuItemIntroduceSelfToEveryone;
    private javax.swing.JMenuItem jMenuItemMotherlode;
    private javax.swing.JMenuItem jMenuItemNoWindows;
    private javax.swing.JMenuItem jMenuItemOptions;
    private javax.swing.JMenuItem jMenuItemPromote;
    private javax.swing.JMenuItem jMenuItemRosebud;
    private javax.swing.JMenuItem jMenuItemSetMoney;
    private javax.swing.JMenuItem jMenuItemSetSkill;
    private javax.swing.JMenuItem jMenuItemWithdrawMoney;
    private javax.swing.JMenu jMenuMoney;
    private javax.swing.JMenu jMenuRelationshipCheats;
    private javax.swing.JMenu jMenuSkillCheats;
    private javax.swing.JMenu jMenuToggles;
    private javax.swing.JMenu jMenuView;
    private javax.swing.JMenu jMenuWindow;
    private javax.swing.JPanel jPanelAbout;
    private javax.swing.JPanel jPanelCommandStatus;
    private javax.swing.JPanel jPanelConsole;
    private javax.swing.JPanel jPanelInitialSettings;
    private javax.swing.JPanel jPanelMCCC;
    private javax.swing.JPanel jPanelMoneyChooser;
    private javax.swing.JPanel jPanelMoneyChooserButtons;
    private javax.swing.JPanel jPanelRelationshipButtons;
    private javax.swing.JPanel jPanelRelationshipRelationshipType;
    private javax.swing.JPanel jPanelRelationshipRelationshipValue;
    private javax.swing.JPanel jPanelRelationshipSimChooser;
    private javax.swing.JPanel jPanelSettingsAppearance;
    private javax.swing.JPanel jPanelSettingsButtons;
    private javax.swing.JPanel jPanelSettingsCheatsConsole;
    private javax.swing.JPanel jPanelSettingsConsoleGUI;
    private javax.swing.JPanel jPanelSettingsEditor;
    private javax.swing.JPanel jPanelSettingsEditorColour;
    private javax.swing.JPanel jPanelSettingsGeneral;
    private javax.swing.JPanel jPanelSettingsImportExport;
    private javax.swing.JPanel jPanelSettingsKeyBindings;
    private javax.swing.JPanel jPanelSettingsMenuBindings;
    private javax.swing.JPanel jPanelSettingsMenuItemShortcut;
    private javax.swing.JPanel jPanelSettingsMiscellaneous;
    private javax.swing.JPanel jPanelSettingsOkApplyCancel;
    private javax.swing.JPanel jPanelSettingsShortcutControlKeys;
    private javax.swing.JPanel jPanelSettingsSizing;
    private javax.swing.JPanel jPanelSettingsTimeOut;
    private javax.swing.JPanel jPanelSystemTrayMenu;
    private javax.swing.JPanel jPanelTMEX;
    private javax.swing.JPanel jPanelToolbar;
    private javax.swing.JProgressBar jProgressBarStatus;
    private javax.swing.JRadioButton jRadioButtonRelationshipFriendly;
    private javax.swing.JRadioButton jRadioButtonRelationshipRomantic;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane10;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JScrollPane jScrollPane9;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator10;
    private javax.swing.JPopupMenu.Separator jSeparator11;
    private javax.swing.JPopupMenu.Separator jSeparator12;
    private javax.swing.JPopupMenu.Separator jSeparator13;
    private javax.swing.JPopupMenu.Separator jSeparator14;
    private javax.swing.JPopupMenu.Separator jSeparator15;
    private javax.swing.JSeparator jSeparator16;
    private javax.swing.JToolBar.Separator jSeparator2;
    private javax.swing.JToolBar.Separator jSeparator3;
    private javax.swing.JPopupMenu.Separator jSeparator4;
    private javax.swing.JPopupMenu.Separator jSeparator5;
    private javax.swing.JPopupMenu.Separator jSeparator6;
    private javax.swing.JPopupMenu.Separator jSeparator7;
    private javax.swing.JPopupMenu.Separator jSeparator8;
    private javax.swing.JPopupMenu.Separator jSeparator9;
    private javax.swing.JSlider jSliderRelationshipRelationshipValue;
    private javax.swing.JSpinner jSpinnerMoneyChooser;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JTabbedPane jTabbedPaneSettings;
    private javax.swing.JTable jTableAbout;
    private javax.swing.JTable jTableCheatsByType;
    private javax.swing.JTable jTableMCCCSettings;
    private javax.swing.JTable jTableTMexSettings;
    private javax.swing.JTextField jTextFieldConsolePrompt;
    private javax.swing.JTextField jTextFieldKeyStroke;
    private javax.swing.JTextField jTextFieldMaxTimeOut;
    private javax.swing.JTextField jTextFieldSettingsWindowHeight;
    private javax.swing.JTextField jTextFieldSettingsWindowWidth;
    private javax.swing.JToggleButton jToggleButtonCheatsConsole;
    private javax.swing.JToggleButton jToggleButtonCheatsExplorer;
    private javax.swing.JToolBar jToolBarCheatsBar;
    private javax.swing.JToolBar jToolBarStatusBar;
    private javax.swing.JTree jTreeCheatsByType;
    // End of variables declaration//GEN-END:variables
}
