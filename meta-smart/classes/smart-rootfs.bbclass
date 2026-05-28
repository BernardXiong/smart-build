smart_target_bin() {
    tool="$1"
    candidate="${TOPDIR}/toolchains/${TARGET_TC}/bin/${RTT_CC_PREFIX}${tool}"
    if [ -x "$candidate" ]; then
        printf '%s\n' "$candidate"
        return 0
    fi

    candidate="${TOPDIR}/toolchains/${TARGET_TC}/${RTT_CC_PREFIX%-}/bin/${RTT_CC_PREFIX}${tool}"
    if [ -x "$candidate" ]; then
        printf '%s\n' "$candidate"
        return 0
    fi

    bbfatal "Target tool not found: ${RTT_CC_PREFIX}${tool} in ${TOPDIR}/toolchains/${TARGET_TC}"
}

smart_target_libdir() {
    libdir="${TOPDIR}/toolchains/${TARGET_TC}/${RTT_CC_PREFIX%-}/lib"
    if [ ! -d "$libdir" ]; then
        bbfatal "Target library directory not found: $libdir"
    fi
    printf '%s\n' "$libdir"
}

smart_copy_musl_runtime() {
    destdir="$1"
    libdir="$(smart_target_libdir)"
    libc="$libdir/libc.so"

    if [ ! -f "$libc" ]; then
        bbfatal "musl libc not found: $libc"
    fi

    mkdir -p "$destdir"
    cp "$libc" "$destdir/"

    ld_name="$(find "$libdir" -maxdepth 1 -name 'ld-musl-*.so.*' -print | head -1)"
    if [ -z "$ld_name" ]; then
        bbfatal "musl dynamic linker not found in $libdir"
    fi

    # Toolchain linker files are usually absolute symlinks to /lib/libc.so.
    # Copy libc.so under the interpreter name so the rootfs is self-contained.
    cp "$libc" "$destdir/$(basename "$ld_name")"
}

smart_rtthread_sdk_arch() {
    case "${ARCH}" in
        riscv64) printf '%s\n' "risc-v/rv64gc" ;;
        aarch64) printf '%s\n' "aarch64/cortex-a" ;;
        arm) printf '%s\n' "arm/cortex-a" ;;
        *) bbfatal "Unknown ARCH for RT-Thread SDK runtime: ${ARCH}" ;;
    esac
}

smart_copy_rtthread_runtime() {
    destdir="$1"
    sdk_base="$2"
    sdk_arch="$(smart_rtthread_sdk_arch)"
    lib="$sdk_base/lib/$sdk_arch/librtthread.so"

    if [ ! -f "$lib" ]; then
        bbfatal "RT-Thread runtime library not found: $lib"
    fi

    mkdir -p "$destdir"
    cp "$lib" "$destdir/"
}

smart_make_ext4_image() {
    rootfs_dir="$1"
    image="$2"
    size_mb="$3"
    shift 3

    if [ ! -d "$rootfs_dir" ]; then
        bbfatal "rootfs directory not found: $rootfs_dir"
    fi

    rm -f "$image"
    dd if=/dev/zero of="$image" bs=1M count="$size_mb"
    mke2fs -q -F -t ext4 "$@" -d "$rootfs_dir" "$image"
}

smart_drop_lost_found() {
    image="$1"
    if command -v debugfs >/dev/null 2>&1; then
        debugfs -w -R "rmdir /lost+found" "$image" >/dev/null 2>&1 || true
    fi
}

smart_set_inode_owner() {
    image="$1"
    path="$2"
    uid="$3"
    gid="$4"

    if command -v debugfs >/dev/null 2>&1; then
        debugfs -w -R "set_inode_field $path uid $uid" "$image" >/dev/null 2>&1 || true
        debugfs -w -R "set_inode_field $path gid $gid" "$image" >/dev/null 2>&1 || true
    fi
}
