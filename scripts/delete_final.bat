@echo off
del "c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable\test_write.txt"
if exist "c:\Users\YUSUF\AndroidStudioProjects\cuzdan\app\src\main\res\drawable\test_write.txt" (
    echo Still there
) else (
    echo Deleted
)
