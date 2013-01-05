package com.labs.zepdroid.ui.Joystick;

public interface JoystickMovedListener {
	public void OnMoved(JoystickView joystickView, int pan, int tilt);
	public void OnReleased(JoystickView joystickView);
	public void OnReturnedToCenter(JoystickView joystickView);
}
