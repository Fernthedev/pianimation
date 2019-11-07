package com.github.fernthedev.gui;

import com.github.fernthedev.light.api.LightFile;
import com.github.fernthedev.light.api.LightParser;
import com.github.fernthedev.light.api.lines.ILightLine;
import com.github.fernthedev.light.api.lines.LightPinLine;
import com.github.fernthedev.light.api.lines.LightSleepLine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import lombok.NonNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.util.List;
import java.util.*;

public class MainGUI extends JFrame {


    private static final long serialVersionUID = 9179852473314806222L;

    private JList<String> actionList;
    private JButton onButton;
    private JSpinner pin;
    private JSpinner selectedFrame;
    private JSpinner fps;
    private JComboBox<String> whichPi;
    private JTextField saveOpenPath;
    private JPanel panel;
    private JButton saveButton;
    private JButton openButton;
    private JScrollPane scrollPane;
    private JLabel statusText;
    private JCheckBox allPinsCheck;

    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private Map<String, PinData.FrameData> frameDataLogMap = new HashMap<>();

    private List<LightFile> lightFolder;

    private PinData[] pinDatas = new PinData[32];
    private PinData selectedPinData;

    private int pinMax;

    private int currentFrameInt;

    private List<String> actionLog = new ArrayList<>();


    public static void main(String[] args) {
        JFrame frame = new JFrame("MainGUI");
        frame.setContentPane(new MainGUI().panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }


    }

    private MainGUI() {
        whichPi.addActionListener(new ActionListener() {
            /**
             * Invoked when an action occurs.
             *
             * @param e the event to be processed
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                BoardType selectedPi = BoardType.valueOf(Objects.requireNonNull(whichPi.getSelectedItem()).toString());

                pinMax = allPins(selectedPi);
            }
        });

        List<BoardType> boardTypes = Arrays.asList(BoardType.values());
        System.out.println(boardTypes);
        for (BoardType boardType : boardTypes) {
            whichPi.addItem(boardType.toString());
        }

        whichPi.setSelectedIndex(0);

        for (int i = 0; i < pinDatas.length; i++) {
            PinData pinData = new PinData(i);
            pinDatas[i] = pinData;
        }

        currentFrameInt = (int) selectedFrame.getValue();

        selectedPinData = pinDatas[(int) pin.getValue()];

        selectedPinData.getFrames().add(new PinData.FrameData(currentFrameInt, PinData.PinMode.OFF));

        fps.setValue(10);


        statusText.setText("The current status is not set");
        pin.addChangeListener(new ChangeListener() {
            /**
             * Invoked when the target of the listener has changed its state.
             *
             * @param e a ChangeEvent object
             */
            @Override
            public void stateChanged(ChangeEvent e) {
                if ((int) pin.getValue() > pinMax) {
                    pin.setValue(pinMax);
                }

                if ((int) pin.getValue() < 0) {
                    pin.setValue(0);
                }

                selectedPinData = pinDatas[(int) pin.getValue()];
                updateLog();
                if (!selectedPinData.getFrames().isEmpty() && getCurrentFrame() != null) {
                    checkButton(getCurrentFrame());
                }
            }
        });

        selectedFrame.addChangeListener(new ChangeListener() {
            /**
             * Invoked when the target of the listener has changed its state.
             *
             * @param e a ChangeEvent object
             */
            @Override
            public void stateChanged(ChangeEvent e) {
                if ((int) selectedFrame.getValue() < 0) {
                    selectedFrame.setValue(0);
                }

                currentFrameInt = (int) selectedFrame.getValue() - 1;

                int frameTime = currentFrameInt + 1;


                if (selectedPinData.getFrames().toArray().length < frameTime) {
                    int times = frameTime - selectedPinData.getFrames().toArray().length;

                    for (int i = 0; i < times; i++) {
                        int newFrame = frameTime - i;
                        selectedPinData.getFrames().add(new PinData.FrameData(newFrame, PinData.PinMode.OFF));
                    }
                }

                if (currentFrameInt < 0) currentFrameInt = 0;

                if (selectedPinData.getFrames().isEmpty())
                    selectedPinData.getFrames().add(new PinData.FrameData(0, PinData.PinMode.OFF));

                PinData.FrameData frameData = selectedPinData.getFrames().get(currentFrameInt);
                updateFrame(frameData);
            }
        });

        onButton.addActionListener(new ActionListener() {
            /**
             * Invoked when an action occurs.
             *
             * @param e the event to be processed
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                statusText.setText(String.valueOf(selectedPinData.getFrames().toArray().length));

                checkButton(getCurrentFrame());
            }
        });

        saveButton.addActionListener(new ActionListener() {
            /**
             * Invoked when an action occurs.
             *
             * @param e the event to be processed
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                if (getSelectedPath() != null) {
                    SaveDialog saveDialog = new SaveDialog(null);

                    saveDialog.setVisible(true);

                    JProgressBar progressBar = saveDialog.getProgressBar1();
                    JLabel label = saveDialog.getLoadingLabel();
                    progressBar.setValue(progressBar.getMinimum());

                    label.setText("Converting file to data.");
                    registerLightFile(getSelectedPath());
                    progressBar.setValue(progressBar.getMaximum() / 40);

                    try {
                        LightParser.saveFolder(lightFolder);
                        List<String> strings = new ArrayList<>();

                        BoardType selectedPi = BoardType.valueOf(Objects.requireNonNull(whichPi.getSelectedItem()).toString());

                        strings.add(gson.toJson(new Properties((Integer) fps.getValue(), selectedPi)));

                        Files.write(new File(getSelectedPath(), "config.properties").toPath(), strings, StandardCharsets.UTF_8).toFile();

                        progressBar.setValue(progressBar.getMaximum());
                        saveDialog.dispose();
                    } catch (AccessDeniedException e1) {
//custom title, error icon
                        JOptionPane.showMessageDialog(panel,
                                "Unable to access folder. Permission denied. \n" + e1.getMessage(),
                                "Access denied",
                                JOptionPane.ERROR_MESSAGE);
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        });

        openButton.addActionListener(new ActionListener() {
            /**
             * Invoked when an action occurs.
             *
             * @param e the event to be processed
             */
            @Override
            public void actionPerformed(ActionEvent e) {
                if (getSelectedPath() != null) {
                    registerLightFile(getSelectedPath());

                    lightFolder.clear();
                    try {
                        if (getSelectedPath() != null) {
                            lightFolder = LightParser.parseFolder(getSelectedPath());
                        }
                    } catch (FileNotFoundException e1) {
                        e1.printStackTrace();
                    }
                    try {
                        FileReader reader = new FileReader(new File(getSelectedPath(), "config.properties"));
                        Properties properties = gson.fromJson(reader, Properties.class);
                        reader.close();

                        handleProperties(properties);
                    } catch (FileNotFoundException e1) {
                        //custom title, error icon
                        JOptionPane.showMessageDialog(panel,
                                "Cannot find config file. \n" + e1.getMessage(),
                                "Unable to find config file",
                                JOptionPane.ERROR_MESSAGE);
                        e1.printStackTrace();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }

                    for (int i = 0; i < lightFolder.size(); i++) {
                        LightFile lightFile = lightFolder.get(i);
                        PinData pinData = pinDatas[i];
                        PinData.FrameData lastFrame = null;

                        for (int ii = 0; ii < lightFile.getLineList().size(); ii++) {
                            ILightLine lightLine = lightFile.getLineList().get(ii);

                            if (lightLine instanceof LightPinLine) {
                                LightPinLine lightPinLine = (LightPinLine) lightLine;
                                while (pinData.getFrames().size() <= ii) {
                                    pinData.getFrames().add(new PinData.FrameData(ii, PinData.PinMode.OFF));
                                }

                                PinData.FrameData frameData = pinData.getFrames().get(ii);
                                frameData.setAllPins(lightPinLine.getPin() instanceof Boolean && (boolean) lightPinLine.getPin());
                                frameData.setPinMode(PinData.PinMode.fromBoolean(lightPinLine.isToggle()));
                                lastFrame = frameData;
                            }

                            if (lightLine instanceof LightSleepLine) {

                                LightSleepLine lightSleepLine = (LightSleepLine) lightLine;
                                int times = (int) ((int) fps.getValue() * lightSleepLine.getSleepDouble());
                                for (int te = 0; te < times; te++) {
                                    if (lastFrame != null) {
                                        PinData.FrameData newFrame = new PinData.FrameData(lightFile.getLineList().size(), lastFrame.getPinMode());
                                        newFrame.setAllPins(lastFrame.isAllPins());
                                        newFrame.setPinMode(lastFrame.getPinMode());
                                    }
                                }

                            }
                        }

                    }

                    updateLog();
                    checkButton(getCurrentFrame());
                }
            }
        });


        allPinsCheck.addChangeListener(new ChangeListener() {
            /**
             * Invoked when the target of the listener has changed its state.
             *
             * @param e a ChangeEvent object
             */
            @Override
            public void stateChanged(ChangeEvent e) {
                if (getCurrentFrame() != null) {
                    if (allPinsCheck.isSelected()) {
                        getCurrentFrame().setAllPins(true);
                    } else {
                        getCurrentFrame().setAllPins(false);
                    }

                    updateFrame(getCurrentFrame());
                }
            }
        });
    }

    private File getSelectedPath() {
        File file = new File(saveOpenPath.getText());
        if (file.exists() && file.isDirectory()) {
            return file;
        } else {
            if (openFileDialog()) {
                return new File(saveOpenPath.getText());
            } else {
                return null;
            }
        }
    }

    private void registerLightFile(@NonNull File folder) {
        try {
            if (folder.isDirectory()) {
                if (folder.listFiles() != null && Objects.requireNonNull(folder.listFiles()).length > 0) {
                    lightFolder = LightParser.parseFolder(folder);
                } else {
                    if (lightFolder == null) {
                        lightFolder = new ArrayList<>();
                    }

                    PinData.PinMode lastPinMode = null;

                    for (PinData pinData : pinDatas) {
                        List<ILightLine> lightLines = new ArrayList<>();

                        double sleep = 0;
                        int fpsInt = (int) fps.getValue();
                        boolean toSleep = false;


                        for (PinData.FrameData frameData : pinData.getFrames()) {
                            int curLine = lightLines.size();
                            ILightLine lightLine = new LightPinLine(lightLines.size(), pinData.getId(), frameData.getPinMode().toBoolean());

                            if (frameData.isAllPins()) {
                                lightLine = new LightPinLine(lightLines.size(), true, frameData.getPinMode().toBoolean());
                            }

                            if (lastPinMode == frameData.getPinMode()) {
                                sleep += (double) fpsInt / 1000;
                                toSleep = true;
                                continue;
                            }

                            lastPinMode = frameData.getPinMode();


                            if (toSleep) {
                                ILightLine oldLine = lightLine;
                                lightLine = new LightSleepLine(LightSleepLine.formatString(sleep), curLine, sleep);

                                sleep = 0;
                                toSleep = false;

                                lightLines.add(lightLine);
                                lightLines.add(oldLine);

                                continue;
                            }

                            lightLines.add(lightLine);
                        }

                        System.out.println(pinData + " is a thing " + pinData.getId());
                        lightFolder.add(new LightFile(new File(folder, pinData.getId() + ".pia"), lightLines));
                    }
                }

            } else {
                if (openFileDialog()) {
                    registerLightFile(folder);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private boolean openFileDialog() {
        //Create a file chooser
        final JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);


        int result = fc.showOpenDialog(panel);

        if (result == JFileChooser.APPROVE_OPTION) {
            saveOpenPath.setText(fc.getSelectedFile().getPath());
            return true;
        } else {
            return false;
        }
    }

    private void handleProperties(Properties properties) {
        fps.setValue(properties.getFps());
        whichPi.setSelectedItem(properties.getBoardType().toString());
    }

    private void checkButton(PinData.FrameData frameData) {
        String statusNow = onButton.getText();

        boolean toggle = false;

        if (statusNow.equalsIgnoreCase("on")) {
            onButton.setText("OFF");
            toggle = true;
        }
        if (statusNow.equalsIgnoreCase("off")) {
            onButton.setText("ON");
            toggle = false;
        }


        updateFrame(frameData, toggle);
    }

    private void updateFrame(PinData.FrameData frameData, boolean status) {
        PinData.PinMode mode;
        if (status) {
            mode = PinData.PinMode.ON;
        } else {
            mode = PinData.PinMode.OFF;
        }
        if (actionList.getSelectedValuesList() != null && !actionList.getSelectedValuesList().isEmpty() && actionList.getSelectedValue().length() > 1) {
            List<PinData.FrameData> frameDataList = new ArrayList<>();
            for (String string : actionList.getSelectedValuesList()) {
                frameDataLogMap.get(string).setPinMode(mode);
                frameDataList.add(frameDataLogMap.get(string));
            }

            updateFrame(frameDataList.toArray(new PinData.FrameData[0]));
        } else if (actionList.getSelectedValue() != null) {
            PinData.FrameData selectedFrameData = frameDataLogMap.get(actionList.getSelectedValue());
            selectedFrameData.setPinMode(mode);
            updateFrame(selectedFrameData);
        } else {
            frameData.setPinMode(mode);
            updateFrame(frameData);
        }

    }

    private void updateFrame(PinData.FrameData frameData) {
        if (frameData.getPinMode().equals(PinData.PinMode.ON)) {
            statusText.setText("The current status is on");
        } else {
            statusText.setText("The current status is off");
        }

        if (frameData.isAllPins()) {
            pin.setFocusable(false);
            pin.setVisible(false);
        } else {
            pin.setFocusable(true);
            pin.setVisible(true);
        }

        updateLog();
    }

    private void updateFrame(PinData.FrameData... frameDatas) {
        List<PinData.FrameData> frameDataList = new ArrayList<>(Arrays.asList(frameDatas));

        for (PinData.FrameData frameData : frameDataList) {
            updateFrame(frameData);
        }
    }

    private void updateLog() {
        actionList.setListData(new String[0]);
        actionLog.clear();
        frameDataLogMap.clear();

        for (int i = 0; i < selectedPinData.getFrames().size(); i++) {
            PinData.FrameData frameData = selectedPinData.getFrames().get(i);
            String log = "Frame" + frameData.getFrame() + " is " + frameData.getPinMode().toString();
            actionLog.add(log);
            frameDataLogMap.put(log, frameData);
        }
        actionList.setListData(actionLog.toArray(new String[0]));
    }

    private PinData.FrameData getCurrentFrame() {

        if (selectedPinData.getFrames().size() < currentFrameInt) {
            int frameTime = currentFrameInt + 1;


            if (selectedPinData.getFrames().toArray().length < frameTime) {
                int times = frameTime - selectedPinData.getFrames().toArray().length;

                for (int i = 0; i < times; i++) {
                    int newFrame = frameTime - i;
                    selectedPinData.getFrames().add(new PinData.FrameData(newFrame, PinData.PinMode.OFF));
                }
            }

            if (currentFrameInt < 0) currentFrameInt = 0;

            if (selectedPinData.getFrames().isEmpty())
                selectedPinData.getFrames().add(new PinData.FrameData(0, PinData.PinMode.OFF));
        }

        return selectedPinData.getFrames().get(currentFrameInt);
    }

    private int allPins(BoardType selectedPi) {
        int max;
        max = 16;

        // no further pins to add for Model B Rev 1 boards
        if (selectedPi == BoardType.RaspberryPi_B_Rev1) {
            // return pins collection
            return max;
        }

        // add pins exclusive to Model A and Model B (Rev2)
        if (selectedPi == BoardType.RaspberryPi_A ||
                selectedPi == BoardType.RaspberryPi_B_Rev2) {
            max = 20;
        }

        // add pins exclusive to Models A+, B+, 2B, 3B, and Zero
        else {
            max = 31;
        }

        // return pins collection
        return max;
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        panel = new JPanel();
        panel.setLayout(new GridLayoutManager(17, 5, new Insets(0, 0, 0, 0), -1, -1));
        panel.setMinimumSize(new Dimension(816, 489));
        panel.setName(ResourceBundle.getBundle("strings").getString("windowTitle"));
        panel.setPreferredSize(new Dimension(816, 489));
        panel.setBorder(BorderFactory.createTitledBorder(ResourceBundle.getBundle("strings").getString("windowTitle")));
        final Spacer spacer1 = new Spacer();
        panel.add(spacer1, new GridConstraints(13, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel.add(spacer2, new GridConstraints(14, 1, 3, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1, GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        scrollPane = new JScrollPane();
        panel.add(scrollPane, new GridConstraints(0, 0, 14, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        actionList = new JList<>();
        final DefaultListModel<String> defaultListModel1 = new DefaultListModel<String>();
        actionList.setModel(defaultListModel1);
        scrollPane.setViewportView(actionList);
        onButton = new JButton();
        onButton.setText("On");
        panel.add(onButton, new GridConstraints(16, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pin = new JSpinner();
        pin.setName(ResourceBundle.getBundle("strings").getString("selected.pin"));
        panel.add(pin, new GridConstraints(1, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        selectedFrame = new JSpinner();
        selectedFrame.setName(ResourceBundle.getBundle("strings").getString("selected.frame"));
        panel.add(selectedFrame, new GridConstraints(3, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fps = new JSpinner();
        fps.setName(ResourceBundle.getBundle("strings").getString("fps"));
        fps.setToolTipText(ResourceBundle.getBundle("strings").getString("fps"));
        panel.add(fps, new GridConstraints(5, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        whichPi = new JComboBox<>();
        whichPi.setName(ResourceBundle.getBundle("strings").getString("pi.preset"));
        panel.add(whichPi, new GridConstraints(9, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        saveOpenPath = new JTextField();
        panel.add(saveOpenPath, new GridConstraints(12, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        saveButton = new JButton();
        saveButton.setText("Save");
        panel.add(saveButton, new GridConstraints(10, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        panel.add(spacer3, new GridConstraints(10, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        openButton = new JButton();
        openButton.setText("Open");
        panel.add(openButton, new GridConstraints(10, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer4 = new Spacer();
        panel.add(spacer4, new GridConstraints(10, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, ResourceBundle.getBundle("strings").getString("selected.pin"));
        panel.add(label1, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, ResourceBundle.getBundle("strings").getString("selected.frame"));
        panel.add(label2, new GridConstraints(2, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        this.$$$loadLabelText$$$(label3, ResourceBundle.getBundle("strings").getString("fps"));
        panel.add(label3, new GridConstraints(4, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        this.$$$loadLabelText$$$(label4, ResourceBundle.getBundle("strings").getString("pi.preset"));
        panel.add(label4, new GridConstraints(8, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        this.$$$loadLabelText$$$(label5, ResourceBundle.getBundle("strings").getString("path"));
        panel.add(label5, new GridConstraints(11, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        statusText = new JLabel();
        statusText.setText("");
        panel.add(statusText, new GridConstraints(15, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        allPinsCheck = new JCheckBox();
        this.$$$loadButtonText$$$(allPinsCheck, ResourceBundle.getBundle("strings").getString("all.pins"));
        panel.add(allPinsCheck, new GridConstraints(2, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        label1.setLabelFor(pin);
        label2.setLabelFor(selectedFrame);
        label3.setLabelFor(fps);
        label4.setLabelFor(whichPi);
        label5.setLabelFor(saveOpenPath);
        statusText.setLabelFor(scrollPane);
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadLabelText$$$(JLabel component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setDisplayedMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
        StringBuffer result = new StringBuffer();
        boolean haveMnemonic = false;
        char mnemonic = '\0';
        int mnemonicIndex = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&') {
                i++;
                if (i == text.length()) break;
                if (!haveMnemonic && text.charAt(i) != '&') {
                    haveMnemonic = true;
                    mnemonic = text.charAt(i);
                    mnemonicIndex = result.length();
                }
            }
            result.append(text.charAt(i));
        }
        component.setText(result.toString());
        if (haveMnemonic) {
            component.setMnemonic(mnemonic);
            component.setDisplayedMnemonicIndex(mnemonicIndex);
        }
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panel;
    }

    public enum BoardType {
        UNKNOWN,
        //------------------------
        RaspberryPi_A,
        RaspberryPi_B_Rev1,
        RaspberryPi_B_Rev2,
        RaspberryPi_A_Plus,
        RaspberryPi_B_Plus,
        RaspberryPi_ComputeModule,
        RaspberryPi_2B,
        RaspberryPi_3B,
        RaspberryPi_3B_Plus,
        RaspberryPi_Zero,
        RaspberryPi_ComputeModule3,
        RaspberryPi_ZeroW,
        RaspberryPi_Alpha,
        RaspberryPi_Unknown,
        //------------------------
        // (LEMAKER BANANAPI)
        BananaPi,
        BananaPro,
        //------------------------
        // (SINOVOIP BANANAPI)  (see: https://github.com/BPI-SINOVOIP/WiringPi/blob/master/wiringPi/wiringPi_bpi.c#L1318)
        Bpi_M1,
        Bpi_M1P,
        Bpi_M2,
        Bpi_M2P,
        Bpi_M2P_H2_Plus,
        Bpi_M2P_H5,
        Bpi_M2U,
        Bpi_M2U_V40,
        Bpi_M2M,
        Bpi_M3,
        Bpi_R1,
        Bpi_M64,
        //------------------------
        Odroid,
        //------------------------
        OrangePi,
        //------------------------
        NanoPi_M1,
        NanoPi_M1_Plus,
        NanoPi_M3,
        NanoPi_NEO,
        NanoPi_NEO2,
        NanoPi_NEO2_Plus,
        NanoPi_NEO_Air,
        NanoPi_S2,
        NanoPi_A64,
        NanoPi_K2
        //------------------------
    }


}
