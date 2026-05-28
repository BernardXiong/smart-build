def smart_root(d):
    import os

    root = d.getVar('SMARTROOT')
    if root:
        return os.path.abspath(os.path.expanduser(d.expand(root)))

    layer = d.getVar('LAYERDIR_smart')
    if layer:
        return os.path.abspath(os.path.dirname(os.path.expanduser(d.expand(layer))))

    recipe = d.getVar('FILE')
    if recipe:
        cur = os.path.abspath(recipe)
        while cur and cur != os.path.dirname(cur):
            if os.path.basename(cur) == 'meta-smart':
                return os.path.abspath(os.path.dirname(cur))
            cur = os.path.dirname(cur)

    return None

def smart_unique_paths(paths):
    import os

    seen = set()
    result = []
    for path in paths:
        if not path:
            continue
        norm = os.path.abspath(os.path.expanduser(path))
        if norm in seen:
            continue
        seen.add(norm)
        result.append(norm)
    return result

def smart_toolchain_compiler(path, prefix):
    import os

    if not path or not prefix:
        return None

    triplet = prefix[:-1] if prefix.endswith('-') else prefix
    candidates = [
        os.path.join(path, 'bin', prefix + 'gcc'),
        os.path.join(path, triplet, 'bin', prefix + 'gcc'),
    ]
    for candidate in candidates:
        if os.path.isfile(candidate) and os.access(candidate, os.X_OK):
            return candidate
    return None

def smart_find_host_toolchain(d, prefix=None):
    import os

    prefix = prefix or d.getVar('RTT_CC_PREFIX')
    local_tc = d.getVar('LOCAL_TC')
    target_tc = d.getVar('TARGET_TC')
    root = smart_root(d)

    package_roots = [
        d.getVar('SMART_HOST_PACKAGES_DIR'),
        '~/.env/tools/scripts/packages',
    ]
    if root:
        package_roots.append(os.path.join(root, 'toolchains'))

    exact_names = [local_tc, target_tc]
    for package_root in smart_unique_paths(package_roots):
        for name in [n for n in exact_names if n]:
            path = os.path.join(package_root, name)
            compiler = smart_toolchain_compiler(path, prefix)
            if compiler:
                return path

    for package_root in smart_unique_paths(package_roots):
        if not os.path.isdir(package_root):
            continue
        for entry in sorted(os.listdir(package_root)):
            path = os.path.join(package_root, entry)
            if not os.path.isdir(path):
                continue
            compiler = smart_toolchain_compiler(path, prefix)
            if compiler:
                return path

    return None

def smart_download_dirs(d):
    import os

    root = smart_root(d)
    paths = [
        d.expand(d.getVar('DL_DIR') or ''),
    ]
    if root:
        paths.append(os.path.join(root, 'downloads'))
    paths.append(os.path.join(d.getVar('TOPDIR') or '', 'downloads'))
    return [path for path in smart_unique_paths(paths) if os.path.isdir(path)]

def smart_find_download_archive(d, names=None, patterns=None):
    import fnmatch
    import os

    archive_exts = (
        '.tar', '.tar.gz', '.tgz', '.tar.bz2', '.tbz2',
        '.tar.xz', '.txz', '.zip',
    )
    names = set(names or [])
    patterns = patterns or []

    for download_dir in smart_download_dirs(d):
        for name in names:
            path = os.path.join(download_dir, name)
            if os.path.isfile(path):
                return path

    for download_dir in smart_download_dirs(d):
        for base, dirs, files in os.walk(download_dir):
            depth = os.path.relpath(base, download_dir).count(os.sep)
            if depth >= 3:
                dirs[:] = []
            for filename in sorted(files):
                if not filename.endswith(archive_exts):
                    continue
                if filename in names or any(fnmatch.fnmatch(filename, pattern) for pattern in patterns):
                    return os.path.join(base, filename)

    return None

def handle_machine(d):
    bsps = {
        'qemuarm64':   {'ARCH': 'aarch64', 'BSP': 'bsp/qemu-virt64-aarch64'},
        'qemuriscv64': {'ARCH': 'riscv64', 'BSP': 'bsp/qemu-virt64-riscv'},
        'qemuarm':     {'ARCH': 'arm',     'BSP': 'bsp/qemu-vexpress-a9'},
        'qemuarm32':   {'ARCH': 'arm',     'BSP': 'bsp/qemu-vexpress-a9'},
        'raspi4-64':   {'ARCH': 'aarch64', 'BSP': 'bsp/raspberry-pi/raspi4-64'},
        'rk3500':      {'ARCH': 'aarch64', 'BSP': 'bsp/rockchip/rk3500'},
        'k230':        {'ARCH': 'riscv64', 'BSP': 'bsp/k230'},
    }

    machine = d.getVar('MACHINE')
    if machine not in bsps:
        bb.fatal("Unsupported MACHINE: %s" % machine)

    cfg = bsps[machine]
    d.setVar('BSP', cfg['BSP'])
    d.setVarFlag('BSP', 'export', '1')

    d.setVar('ARCH', cfg['ARCH'])
    d.setVarFlag('ARCH', 'export', '1')

    prefix_by_arch = {
        'aarch64': 'aarch64-linux-musleabi-',
        'riscv64': 'riscv64-linux-musleabi-',
        'arm': 'arm-linux-musleabi-',
    }

    # Allow local.conf or a machine .conf to override the compiler prefix.
    # Otherwise use the common RT-Smart musl toolchain for the architecture.
    if not d.getVar('RTT_CC_PREFIX'):
        d.setVar('RTT_CC_PREFIX', d.getVar('SMART_CC_PREFIX') or prefix_by_arch[cfg['ARCH']])
    d.setVarFlag('RTT_CC_PREFIX', 'export', '1')

def toolchain_for_machine(d):
    toolchains = {
        "aarch64-linux-musleabi-": {
            "URL": "https://download.rt-thread.org/download/rt-smart/toolchains/aarch64-linux-musleabi_for_x86_64-pc-linux-gnu_latest.tar.bz2",
            "LOCAL_TC": "aarch64-linux-musleabi-gcc-latest",
            "TARGET_TC": "aarch64-linux-musleabi_for_x86_64-pc-linux-gnu"
        }, 
        "riscv64-linux-musleabi-": {
            "URL": "https://download.rt-thread.org/download/rt-smart/toolchains/riscv64-linux-musleabi_for_x86_64-pc-linux-gnu_latest.tar.bz2",
            "LOCAL_TC": "riscv64-linux-musleabi-gcc-latest",
            "TARGET_TC": "riscv64-linux-musleabi_for_x86_64-pc-linux-gnu"
        },
        "arm-linux-musleabi-": {
            "URL": "https://download.rt-thread.org/download/rt-smart/toolchains/arm-linux-musleabi_for_x86_64-pc-linux-gnu_latest.tar.bz2",
            "LOCAL_TC": "arm-linux-musleabi-gcc-latest",
            "TARGET_TC": "arm-linux-musleabi_for_x86_64-pc-linux-gnu"
        }
    }

    # handle machine firstly
    handle_machine(d)

    prefix = d.getVar('RTT_CC_PREFIX')
    if prefix not in toolchains:
        bb.fatal("Unsupported RTT_CC_PREFIX for MACHINE %s: %s" % (d.getVar('MACHINE'), prefix))

    d.setVar('URL_TC', toolchains[prefix]["URL"])
    d.setVar('LOCAL_TC', toolchains[prefix]["LOCAL_TC"])
    d.setVar('TARGET_TC', toolchains[prefix]["TARGET_TC"])
    d.setVarFlag('TARGET_TC', 'export', '1')

    return toolchains[prefix]["URL"]
