include ../common.mk

#############################################################
# Linux
ifeq "$(shell uname)" "Linux"

LDFLAGS += -shared -lX11 -lGL
LIBJGL_OBJS = glcontext-x11.o

endif

#############################################################
# MacOS X
ifeq "$(shell uname)" "Darwin"

LDFLAGS += -lX11 -lGL
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
	$(CC) $(CCFLAGS) $(JNI_INCLUDES) $<

april_vis_GL.h: ../../src/april/vis/GL.java
	@echo "NOTICE: Rebuilding JNI headers. Ensure java file has been recently built."
	javah -classpath ../../april.jar  april.vis.GL

clean:
	rm -rf $(LIBJGL_OBJS) *~ april_vis_GL.h $(LIB) $(DESTLIB)
