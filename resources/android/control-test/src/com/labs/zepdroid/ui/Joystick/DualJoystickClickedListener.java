package com.labs.zepdroid.ui.Joystick;

public interface DualJoystickClickedListener {
	public void OnClicked(JoystickSide side, JoystickView joystickView);
	public void OnReleased(JoystickSide side, JoystickView joystickView);
}
