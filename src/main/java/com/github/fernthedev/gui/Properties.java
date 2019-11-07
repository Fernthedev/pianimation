package com.github.fernthedev.gui;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class Properties implements Serializable {
    private int fps;
    private MainGUI.BoardType boardType;

}
