#ifndef GTKHELPER_H
#define GTKHELPER_H

//XXX debug
static void check_mask(GtkWidget * self)
{
    char * names[] = {"GDK_EXPOSURE_MASK",
                      "GDK_POINTER_MOTION_MASK",
                      "GDK_POINTER_MOTION_HINT_MASK",
                      "GDK_BUTTON_MOTION_MASK",
                      "GDK_BUTTON1_MOTION_MASK",
                      "GDK_BUTTON2_MOTION_MASK",
                      "GDK_BUTTON3_MOTION_MASK",
                      "GDK_BUTTON_PRESS_MASK",
                      "GDK_BUTTON_RELEASE_MASK",
                      "GDK_KEY_PRESS_MASK",
                      "GDK_KEY_RELEASE_MASK",
                      "GDK_ENTER_NOTIFY_MASK",
                      "GDK_LEAVE_NOTIFY_MASK",
                      "GDK_FOCUS_CHANGE_MASK",
                      "GDK_STRUCTURE_MASK",
                      "GDK_PROPERTY_CHANGE_MASK",
                      "GDK_VISIBILITY_NOTIFY_MASK",
                      "GDK_PROXIMITY_IN_MASK",
                      "GDK_PROXIMITY_OUT_MASK",
                      "GDK_SUBSTRUCTURE_MASK",
                      "GDK_SCROLL_MASK",
                      "GDK_TOUCH_MASK",
                      "GDK_SMOOTH_SCROLL_MASK",
                      "GDK_ALL_EVENTS_MASK"};
    int masks[24];

    for (int i = 0; i< 23; i++)
        masks[i] = 1 << i;
    masks[23]= 0xFFFFFE;

    int mask = gtk_widget_get_events(GTK_WIDGET(self));

    printf("Checking masks:\n");
    for (int i = 0; i < 24; i++) {
        if (mask & masks[i])
            printf("Has mask %s %d\n", names[i], i);
    }
}


#endif
