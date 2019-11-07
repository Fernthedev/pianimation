package com.github.fernthedev.gui;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PinData {


    private int id;
    private List<FrameData> frameDatas = new ArrayList<>();

    public PinData(int id) {
        this.id = id;
    }

    public List<FrameData> getFrames() {
        return frameDatas;
    }

    public int getId() {
        return id;
    }


    @Setter
    @Getter
    @Data
    public static class FrameData implements Serializable{

        private static final long serialVersionUID = -5821701319155768090L;

        private final int frame;
        private PinMode pinMode;

        private boolean allPins = false;

        public FrameData(int frame, PinMode pinm) {
            this.frame = frame;
            this.pinMode = pinm;

        }



        public int getFrame()
        {
            return frame;
        }


    }


    public enum PinMode
    {
        ON,
        OFF;

        public static PinMode fromBoolean(boolean mode) {
            if(mode) return ON; else return OFF;
        }

        public boolean toBoolean() {
            return this == ON;
        }
    }
}
