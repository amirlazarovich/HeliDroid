package com.labs.commons;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author Amir Lazarovich
 * @version 0.1
 */
public class AnimUtils {
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////
    public static final int SHORT_ANIM_TIME = 100;
    public static final int MEDIUM_ANIM_TIME = 250;

    ///////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////
    /**
     * Display/Hide selected view with animation
     *
     * @param show Whether to show or hide
     * @param view
     */
    public static void showViewAnimated(boolean show, final View view, int duration) {
        showViewAnimated(show, null, view, duration);
    }

    /**
     * Display selected view with animation
     *
     * @param view
     */
    public static void showViewAnimated(View view, int duration) {
        showViewAnimated(true, null, view, duration);
    }

    /**
     * Prepare an {@link android.animation.Animator} object that contains animation that will
     * display selected view
     *
     * @param view
     * @param duration
     * @return
     */
    public static Animator prepareShowViewAnimated(View view, int duration) {
        return prepareShowViewAnimated(true, null, view, duration);
    }

    /**
     * Hide selected view with animation
     *
     * @param view
     * @param duration
     */
    public static void hideViewAnimated(View view, int duration) {
        showViewAnimated(false, null, view, duration);
    }

    /**
     * Prepare an {@link android.animation.Animator} object that contains animation that will
     * hide selected view
     *
     * @param view
     * @param duration
     * @return
     */
    public static Animator prepareHideViewAnimated(View view, int duration) {
        return prepareShowViewAnimated(false, null, view, duration);
    }

    /**
     * Attach selected view to selected parent with animation
     *
     * @param parent
     * @param view
     * @param duration
     */
    public static void attachViewAnimated(ViewGroup parent, View view, int duration) {
        showViewAnimated(true, parent, view, duration);
    }

    /**
     * Prepare an {@link android.animation.Animator} object that contains animation that will
     * attach selected view to selected parent with animation
     *
     * @param parent
     * @param view
     * @param duration
     * @return
     */
    public static Animator prepareAttachViewAnimated(ViewGroup parent, View view, int duration) {
        return prepareShowViewAnimated(true, parent, view, duration);
    }

    /**
     * Detach view from selected parent with animation
     *
     * @param parent
     * @param view
     * @param duration
     */
    public static void detachViewAnimated(ViewGroup parent, View view, int duration) {
        showViewAnimated(false, parent, view, duration);
    }

    /**
     * Prepare an {@link android.animation.Animator} object that contains animation that will
     * detach selected view from selected parent with animation
     *
     * @param parent
     * @param view
     * @param duration
     * @return
     */
    public static Animator prepareDetachViewAnimated(ViewGroup parent, View view, int duration) {
        return prepareShowViewAnimated(false, parent, view, duration);
    }

    /**
     * Sets up an AnimatorSet to play all of the supplied animations at the same time.
     *
     * @param items The animations that will be started simultaneously.
     */
    public static AnimatorSet playTogether(Animator... items) {
        if (items == null) {
            return null;
        }

        Collection<Animator> animators = new HashSet<Animator>(items.length);
        for (Animator anim : items) {
            if (anim != null) {
                animators.add(anim);
            }
        }

        AnimatorSet animSet = null;
        if (!animators.isEmpty()) {
            animSet = new AnimatorSet();
            animSet.playTogether(animators);
            animSet.start();
        }

        return animSet;
    }

    /**
     * Sets up an AnimatorSet to play all of the supplied animations one after the other.
     *
     * @param items The animations that will be started simultaneously.
     */
    public static AnimatorSet playSequentially(Animator... items) {
        if (items == null) {
            return null;
        }

        List<Animator> animators = new ArrayList<Animator>(items.length);
        for (Animator anim : items) {
            if (anim != null) {
                animators.add(anim);
            }
        }

        AnimatorSet animSet = null;
        if (!animators.isEmpty()) {
            animSet = new AnimatorSet();
            animSet.playSequentially(animators);
            animSet.start();
        }

        return animSet;
    }


    ///////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////
    /**
     * Show view animated and decide whether to attach/detach our view to our parent before/after the animation
     *
     * @param show
     * @param parent
     * @param view
     * @param duration
     */
    private static void showViewAnimated(boolean show, ViewGroup parent, View view, int duration) {
        Animator anim = prepareShowViewAnimated(show, parent, view, duration);
        if (anim != null) {
            anim.start();
        }
    }

    /**
     * Prepare to show view animated and decide whether to attach/detach our view to our parent before/after the animation
     *
     * @param show
     * @param parent
     * @param view
     * @param duration
     */
    private static Animator prepareShowViewAnimated(boolean show, final ViewGroup parent, final View view, int duration) {
        Animator anim = null;
        if (view == null) {
            return anim;
        }

        if (show && view.getVisibility() != View.VISIBLE) {
            // show
            view.setEnabled(true);
            view.setVisibility(View.VISIBLE);
            if (parent != null) {
                // attach view to its parent
                parent.addView(view);
            }

            anim = ObjectAnimator.ofFloat(view, "alpha", 0, 1).setDuration(duration);
        } else if (!show && view.getVisibility() == View.VISIBLE) {
            // hide
            view.setEnabled(false);
            anim = ObjectAnimator.ofFloat(view, "alpha", 1, 0).setDuration(duration);
            anim.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    view.setVisibility(View.GONE);

                    if (parent != null) {
                        // detach view from its parent
                        parent.removeView(view);
                    }
                }

                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
        }

        return anim;
    }
}
