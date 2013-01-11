#############################################################
# Linux
ifeq "$(shell uname)" "Linux"

CC = gcc -c
CCFLAGS = -Wall -g -std=gnu99 -fno-omit-frame-pointer -fno-stack-protector -D_REENTRANT -fPIC -shared -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -Wno-unused-parameter -Wno-unused-variable -Wno-format-zero-length
LD = ld
LDFLAGS = --shared
SHARED_EXT = so

# parse last line of update-alternatives --display to find out where the currently selected jvm lives, then make include path
JNI_INCLUDES = -I$(shell update-alternatives --display java | grep current | sed "s:.*\(/usr/.*\)/jre/bin/java.*$$:\1/include:")
#JNI_INCLUDES = -I/usr/lib/jvm/java-6-sun/include/ -I/usr/lib/jvm/java-6-sun/include/linux -I/usr/lib/jvm/java-6-openjdk/include/
endif

#############################################################
# MacOS X
ifeq "$(shell uname)" "Darwin"

CC = gcc -c
CCFLAGS = -std=gnu99 -O2 -D_REENTRANT -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -Wno-unused-parameter -Wno-format-zero-length
LD = gcc
LDFLAGS = -dynamiclib -framework OpenGL -framework JavaVM -L/usr/X11/lib
SHARED_EXT = jnilib

JNI_INCLUDES = -I/System/Library/Frameworks/JavaVM.framework/Headers/ -I/usr/X11/include/

endif
