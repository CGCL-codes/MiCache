#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/sendfile.h>

int main(int argc, char *argv[])
{
	if (argc < 3)
		return -1;

	int infd = open(argv[1], O_RDONLY);
	int outfd = open(argv[2], O_RDWR);
	if (infd < 0 || outfd < 0)
		return -1;

	if (lseek(outfd, 2, SEEK_SET) < 0)
		return -1;

	// off_t off = 2;
	long long data;
	read(infd, &data, 1);

	if (sendfile(outfd, infd, NULL, 8) < 0)
		return -1;

	close(infd);
	close(outfd);
	printf("done\n\r");
	return 0;
}
