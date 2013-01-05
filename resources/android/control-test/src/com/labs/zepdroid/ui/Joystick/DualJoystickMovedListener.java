package com.labs.zepdroid.ui.Joystick;

public interface DualJoystickMovedListener {
	public void OnMoved(JoystickSide side, JoystickView joystickView, int pan, int tilt);
	public void OnReleased(JoystickSide side, JoystickView joystickView);
	public void OnReturnedToCenter(JoystickSide side, JoystickView joystickView);
}
