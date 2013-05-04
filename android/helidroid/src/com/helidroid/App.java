package com.helidroid;

import android.app.Application;
import com.helidroid.commons.Const;

/**
 * @author Amir Lazarovich
 */
public class App extends Application {

    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////


    ///////////////////////////////////////////////
    // Members
    ///////////////////////////////////////////////
    public static Const sConsts;

    ///////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////


    ///////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////


    ///////////////////////////////////////////////
    // Overrides & Implementations
    ///////////////////////////////////////////////

    @Override
    public void onCreate() {
        super.onCreate();

        sConsts = new Const(this);
    }


    ///////////////////////////////////////////////
    // Getters & Setters
    ///////////////////////////////////////////////


    ///////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////


}
