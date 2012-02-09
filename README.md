Usage
=====

```bash
$ seq 3000000 | ./sametime "sed s/0/x/g"
```

Why not...
==========

* GNU Parallel? If you ask it to keep lines in the same order as the input, it tends to eat all of your memory. Or more.
* Bash? 'wait' doesn't throw error codes and those are really important. Ways of hacking around this found on Stack Overflow also tend to fail (e.g. http://stackoverflow.com/questions/356100/how-to-wait-in-bash-for-several-subprocesses-to-finish-and-return-exit-code-0)
