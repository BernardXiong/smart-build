inherit machine
inherit smart-rootfs

SUMMARY = "Minimal hello-world rootfs image"
DESCRIPTION = "Builds an ext4 rootfs image containing a hello-world C program (static or dynamic)."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

MINI_ROOTFS_IMAGE ?= "mini_rootfs.img"
MINI_ROOTFS_SIZE_MB ?= "4"
MINI_ROOTFS_LINK ?= "static"

python () {
    toolchain_for_machine(d)
}

do_build_mini_rootfs() {
    bbplain "##############################"
    bbplain "****** Building minimal rootfs for ${MACHINE} (${ARCH})"

    mini_cc="$(smart_target_bin gcc)"

    mini_src="${FILE_DIRNAME}/files/hello.c"
    mini_build_dir="${WORKDIR}/mini-rootfs"
    mini_rootfs_dir="$mini_build_dir/rootfs"
    mini_image="$mini_build_dir/${MINI_ROOTFS_IMAGE}"

    rm -rf "$mini_build_dir"
    mkdir -p "$mini_rootfs_dir" "${TOPDIR}/${MACHINE}"

    if [ "${MINI_ROOTFS_LINK}" = "dynamic" ]; then
        bbplain "****** Compiling dynamic hello world: $mini_cc"
        unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS
        "$mini_cc" -Os -s "$mini_src" -o "$mini_rootfs_dir/hello"
        chmod 0755 "$mini_rootfs_dir/hello"

        bbplain "****** Copying musl shared libraries"
        smart_copy_musl_runtime "$mini_rootfs_dir/lib"
    else
        bbplain "****** Compiling static hello world: $mini_cc"
        unset CFLAGS CXXFLAGS CPPFLAGS LDFLAGS
        "$mini_cc" -static -Os -s "$mini_src" -o "$mini_rootfs_dir/hello"
        chmod 0755 "$mini_rootfs_dir/hello"
    fi

    bbplain "****** Creating ${MINI_ROOTFS_SIZE_MB}MiB ext4 image with only /hello"
    smart_make_ext4_image "$mini_rootfs_dir" "$mini_image" "${MINI_ROOTFS_SIZE_MB}" \
        -m 0 -O ^has_journal -E root_owner=0:0
    smart_drop_lost_found "$mini_image"
    smart_set_inode_owner "$mini_image" /hello 0 0

    cp "$mini_image" "${TOPDIR}/${MACHINE}/${MINI_ROOTFS_IMAGE}"
    bbplain "****** Installed minimal rootfs image to: ${TOPDIR}/${MACHINE}/${MINI_ROOTFS_IMAGE}"
}
do_build_mini_rootfs[depends] = "smart-gcc:do_install_toolchain"

addtask do_build_mini_rootfs
