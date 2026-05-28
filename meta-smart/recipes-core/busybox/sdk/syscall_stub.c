#include <errno.h>
#include <stdarg.h>

__attribute__((visibility("default")))
long syscall(long number, ...)
{
	/* RT-Thread Smart does not support Linux-style syscall numbers.
	 * Busybox features that rely on direct syscall() will get ENOSYS. */
	(void)number;
	errno = ENOSYS;
	return -1;
}
