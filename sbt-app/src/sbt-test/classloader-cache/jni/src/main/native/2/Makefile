TARGET_DIR := ../../../target
BUILD_DIR := $(TARGET_DIR)/build
NATIVE_DIR := native/$(shell uname -sm | cut -d ' ' -f2 | tr '[:upper:]' '[:lower:]')
LIB_DIR := ../resources/$(NATIVE_DIR)
LIB_NAME := sbt-jni-library-test0
POSIX_LIB_NAME := lib$(LIB_NAME)
SOURCE := sbt_JniLibrary
CC := clang

UNAME_S := $(shell uname -s)

ifeq ($(UNAME_S),Darwin)
	JNI_INCLUDE := -I$(shell mdfind -name jni.h | grep jdk1.8 | tail -n 1 | xargs dirname)\
		-I$(shell mdfind -name jni_md.h | grep jdk1.8 | tail -n 1 | xargs dirname)
	LD := /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin/c++
	OBJS := $(BUILD_DIR)/x86_64/darwin/$(SOURCE).o
	LIBS := $(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).dylib
endif

ifeq ($(UNAME_S), Linux)
	BASE_INCLUDE := $(shell locate jni.h | tail -n 1 | xargs dirname)
	JNI_INCLUDE := -I$(BASE_INCLUDE) -I$(BASE_INCLUDE)/linux
	OBJS := $(BUILD_DIR)/x86_64/linux/$(SOURCE).o
	LIBS := $(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).so
endif

LINUX_CCFLAGS := -fPIC
LINUX_LDFLAGS := -fPIC -shared

CCFLAGS := -I./../include $(JNI_INCLUDE) -Wno-unused-command-line-argument -std=c++11 -O3

all: $(LIBS)

.PHONY: clean all

$(BUILD_DIR)/x86_64/linux/$(SOURCE).o: $(SOURCE).cc
	mkdir -p $(BUILD_DIR)/x86_64/linux; \
	$(CC) -c $< $(CCFLAGS) $(JNI_INCLUDE) -fPIC -o $@

$(BUILD_DIR)/x86_64/darwin/$(SOURCE).o: $(SOURCE).cc
	mkdir -p $(BUILD_DIR)/x86_64/darwin; \
	$(CC) -c $< $(CCFLAGS) -framework Carbon -o $@

$(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).dylib: $(BUILD_DIR)/x86_64/darwin/$(SOURCE).o
	mkdir -p $(TARGET_DIR)/x86_64; \
	$(LD) -dynamiclib -framework Carbon $(CCFLAGS) -Wl,-headerpad_max_install_names -install_name @rpath/$(POSIX_LIB_NAME) \
	$(BUILD_DIR)/x86_64/darwin/$(SOURCE).o  \
	 -o $@ ; \
	mkdir -p ../../resources/2; \
	cp $(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).dylib ../../resources/2



$(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).so: $(BUILD_DIR)/x86_64/linux/$(SOURCE).o
	mkdir -p $(TARGET_DIR)/x86_64; \
	$(CC) -shared $< $(CCFLAGS) -Wl,-headerpad_max_install_names -o $@; \
	mkdir -p ../../resources/2; \
	cp $(TARGET_DIR)/x86_64/$(POSIX_LIB_NAME).so ../../resources/2;

clean:
	rm -rf $(TARGET_DIR)/build $(TARGET_DIR)/$(NATIVE)
