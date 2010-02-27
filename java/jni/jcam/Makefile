GCC = gcc -Wall -g -std=gnu99
LIBIMAGESOURCE_OBJS = image_source.o image_source_v4l2.o image_source_jni.o image_source_dc1394.o url_parser.o
OBJS = image_source.o  image_source_v4l2.o image_source_dc1394.o main.o url_parser.o

all: libimagesource.so
	cp libimagesource.so ../../../lib/

libimagesource.so: april_jcam_ImageSourceNative.h $(LIBIMAGESOURCE_OBJS)
	ld --shared $(LIBIMAGESOURCE_OBJS) -o libimagesource.so -ldc1394

april_jcam_ImageSourceNative.h:
	echo "Rebuilding JNI headers. Ensure java file has been recently built."
	javah -classpath ../../april.jar  april.jcam.ImageSourceNative

clean:
	rm -f $(OBJS) *~ april_jcam_ImageSourceNative.h april_jcam_ImageSourceFormat.h

# If you get an error message saying that you're missing jni.h or
# jni_md.h, locate those files and add the directories using the -I
# flag. See example below

JNI_INCLUDES = -I/usr/lib/jvm/java-6-sun/include/ -I/usr/lib/jvm/java-6-sun/include/linux

%.o: %.c
	$(GCC) -shared -O2 -c -fPIC -fno-omit-frame-pointer -fno-stack-protector -D_REENTRANT $< $(JNI_INCLUDES)
