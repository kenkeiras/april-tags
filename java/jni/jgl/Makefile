#############################################################
# Linux
ifeq "$(shell uname)" "Linux"

CC = gcc
CCFLAGS = -std=gnu99 -fPIC -O2 -D_REENTRANT -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -Wno-unused-parameter -Wno-format-zero-length

JNI_INCLUDES = -I/usr/lib/jvm/java-6-sun/include/ -I/usr/lib/jvm/java-6-sun/include/linux -I/usr/lib/jvm/java-6-openjdk/include/

LD = ld
LDFLAGS = -shared -lX11 -lGL
SHARED_EXT = so

LIBJGL_OBJS = glcontext-x11.o

endif

#############################################################
# MacOS X
ifeq "$(shell uname)" "Darwin"

CC = gcc
CCFLAGS = -std=gnu99 -O2 -D_REENTRANT -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -Wno-unused-parameter -Wno-format-zero-length
JNI_INCLUDES = -I/System/Library/Frameworks/JavaVM.framework/Headers/ -I/usr/X11/include/

LD = gcc
LDFLAGS = -dynamiclib -framework OpenGL -framework JavaVM -L/usr/X11/lib -lX11 -lGL
SHARED_EXT = jnilib

LIBJGL_OBJS = glcontext-x11.o

endif

#############################################################
# Core rules

LIB=libjgl.$(SHARED_EXT)
DESTLIB=../../../lib/$(LIB)

all:  $(DESTLIB)


$(DESTLIB) : $(LIB)
	cp $(LIB) $(DESTLIB)

LIBJGL_OBJS += glcontext.o gl_jni.o lphash.o parray.o
$(LIB): april_vis_GL.h $(LIBJGL_OBJS)
	$(LD) $(LDFLAGS) $(LIBJGL_OBJS) -o $@

#############################################################
%.o: %.c
	$(CC) $(CCFLAGS) -c $(JNI_INCLUDES) $<

april_vis_GL.h: ../../src/april/vis/GL.java
	@echo "NOTICE: Rebuilding JNI headers. Ensure java file has been recently built."
	javah -classpath ../../april.jar  april.vis.GL

clean:
	rm -rf $(LIBJGL_OBJS) *~ april_vis_GL.h $(LIB) $(DESTLIB)
