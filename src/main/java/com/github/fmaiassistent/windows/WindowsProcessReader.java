package com.github.fmaiassistent.windows;

import com.github.fmaiassistent.linux.MemoryRegion;
import com.github.fmaiassistent.linux.ProcessInfo;
import com.github.fmaiassistent.memory.ProcessMemoryReader;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class WindowsProcessReader implements ProcessMemoryReader {
    private static final int PROCESS_QUERY_INFORMATION = 0x0400;
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
    private static final int PROCESS_VM_READ = 0x0010;
    private static final int LIST_MODULES_ALL = 0x03;
    private static final int ERROR_PARTIAL_COPY = 299;
    private static final int TH32CS_SNAPMODULE = 0x00000008;
    private static final int TH32CS_SNAPMODULE32 = 0x00000010;
    private static final int MEM_COMMIT = 0x1000;
    private static final int PAGE_NOACCESS = 0x01;
    private static final int PAGE_READONLY = 0x02;
    private static final int PAGE_READWRITE = 0x04;
    private static final int PAGE_WRITECOPY = 0x08;
    private static final int PAGE_EXECUTE = 0x10;
    private static final int PAGE_EXECUTE_READ = 0x20;
    private static final int PAGE_EXECUTE_READWRITE = 0x40;
    private static final int PAGE_EXECUTE_WRITECOPY = 0x80;
    private static final int PAGE_GUARD = 0x100;
    private static final long MAX_ADDRESS = 0x00007FFFFFFFFFFFL;
    private static final Pointer INVALID_HANDLE_VALUE = Pointer.createConstant(-1);

    private final int pid;
    private final Pointer process;

    public WindowsProcessReader(int pid) throws IOException {
        if (Native.POINTER_SIZE != Long.BYTES) {
            throw new IOException("A 64-bit Java runtime is required to read the 64-bit FM26 process");
        }
        this.pid = pid;
        Pointer handle = Kernel32.INSTANCE.OpenProcess(PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, pid);
        if (handle == null) {
            handle = Kernel32.INSTANCE.OpenProcess(
                    PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, false, pid);
        }
        this.process = handle;
        if (process == null) {
            throw win32Error("OpenProcess(" + pid + ") failed. Run this app as the same Windows user as Football Manager");
        }
    }

    @Override
    public int pid() {
        return pid;
    }

    @Override
    public Platform platform() {
        return Platform.WINDOWS;
    }

    public static List<ProcessInfo> findProcesses(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        List<ProcessInfo> matches = ProcessHandle.allProcesses()
                .map(handle -> {
                    ProcessHandle.Info info = handle.info();
                    String command = info.command().orElse("");
                    String commandLine = info.commandLine().orElse(command);
                    String name = command.isBlank() ? "" : java.nio.file.Path.of(command).getFileName().toString();
                    return new ProcessInfo(Math.toIntExact(handle.pid()), name, commandLine);
                })
                .filter(process -> (process.name() + " " + process.cmdline()).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparingInt(ProcessInfo::pid))
                .toList();
        return new ArrayList<>(matches);
    }

    @Override
    public byte[] readBytes(long address, int size) throws IOException {
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        if (size == 0) {
            return new byte[0];
        }
        Memory buffer = new Memory(size);
        LongByReference bytesRead = new LongByReference();
        boolean success = Kernel32.INSTANCE.ReadProcessMemory(
                process, Pointer.createConstant(address), buffer, size, bytesRead);
        if (!success || bytesRead.getValue() != size) {
            throw win32Error("ReadProcessMemory failed at 0x" + Long.toHexString(address)
                    + " (requested " + size + ", read " + bytesRead.getValue() + ")");
        }
        return buffer.getByteArray(0, size);
    }

    @Override
    public List<MemoryRegion> maps() throws IOException {
        List<ModuleRange> modules = modules();
        List<MemoryRegion> regions = new ArrayList<>();
        long address = 0;
        while (address < MAX_ADDRESS) {
            MemoryBasicInformation mbi = new MemoryBasicInformation();
            long result = Kernel32.INSTANCE.VirtualQueryEx(
                    process, Pointer.createConstant(address), mbi, mbi.size());
            if (result == 0) {
                break;
            }
            mbi.read();
            long start = Pointer.nativeValue(mbi.BaseAddress);
            long size = mbi.RegionSize;
            if (size <= 0 || start > MAX_ADDRESS - size) {
                break;
            }
            long end = start + size;
            if (mbi.State == MEM_COMMIT) {
                String path = modules.stream()
                        .filter(module -> start >= module.start() && start < module.end())
                        .map(ModuleRange::path)
                        .findFirst()
                        .orElse("");
                regions.add(new MemoryRegion(
                        start, end, permissions(mbi.Protect), 0, "", "", path));
            }
            address = Math.max(address + 0x1000, end);
        }
        return regions;
    }

    private List<ModuleRange> modules() throws IOException {
        IOException lastError = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                List<ModuleRange> modules = modulesViaToolhelp();
                if (!modules.isEmpty()) {
                    return modules;
                }
            } catch (IOException ex) {
                lastError = ex;
                if (!ex.getMessage().contains("Windows error " + ERROR_PARTIAL_COPY)) {
                    break;
                }
            }
            try {
                Thread.sleep(50L * (attempt + 1));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while enumerating modules for PID " + pid, interrupted);
            }
        }
        List<ModuleRange> viaPsapi = modulesViaPsapi();
        if (!viaPsapi.isEmpty()) {
            return viaPsapi;
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("No modules enumerated for PID " + pid);
    }

    private List<ModuleRange> modulesViaToolhelp() throws IOException {
        Pointer snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                TH32CS_SNAPMODULE | TH32CS_SNAPMODULE32, pid);
        if (snapshot == null || INVALID_HANDLE_VALUE.equals(snapshot)) {
            throw win32Error("CreateToolhelp32Snapshot failed for PID " + pid);
        }
        try {
            List<ModuleRange> modules = new ArrayList<>();
            ModuleEntry32 entry = new ModuleEntry32();
            entry.dwSize = entry.size();
            entry.write();
            boolean found = Kernel32.INSTANCE.Module32FirstW(snapshot, entry);
            if (!found) {
                throw win32Error("Module32FirstW failed for PID " + pid);
            }
            while (found) {
                entry.read();
                long start = Pointer.nativeValue(entry.modBaseAddr);
                modules.add(new ModuleRange(start, start + Integer.toUnsignedLong(entry.modBaseSize),
                        Native.toString(entry.szExePath)));
                entry.dwSize = entry.size();
                entry.write();
                found = Kernel32.INSTANCE.Module32NextW(snapshot, entry);
            }
            return modules;
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
    }

    private List<ModuleRange> modulesViaPsapi() throws IOException {
        IntByReference needed = new IntByReference();
        Memory probe = new Memory(Native.POINTER_SIZE);
        Kernel32.INSTANCE.K32EnumProcessModulesEx(process, probe, (int) probe.size(), needed, LIST_MODULES_ALL);
        int bytes = needed.getValue();
        if (bytes <= 0) {
            return List.of();
        }
        Memory handles = new Memory(bytes);
        if (!Kernel32.INSTANCE.K32EnumProcessModulesEx(process, handles, bytes, needed, LIST_MODULES_ALL)) {
            return List.of();
        }
        int count = needed.getValue() / Native.POINTER_SIZE;
        List<ModuleRange> modules = new ArrayList<>();
        char[] pathBuffer = new char[260];
        for (int index = 0; index < count; index++) {
            Pointer module = handles.getPointer((long) index * Native.POINTER_SIZE);
            ModuleInfo info = new ModuleInfo();
            if (!Kernel32.INSTANCE.K32GetModuleInformation(process, module, info, info.size())) {
                continue;
            }
            info.read();
            int pathLength = Kernel32.INSTANCE.K32GetModuleFileNameExW(process, module, pathBuffer, pathBuffer.length);
            String path = pathLength > 0 ? Native.toString(pathBuffer) : "";
            long start = Pointer.nativeValue(info.lpBaseOfDll);
            modules.add(new ModuleRange(start, start + Integer.toUnsignedLong(info.SizeOfImage), path));
        }
        return modules;
    }

    private static String permissions(int protect) {
        if ((protect & (PAGE_GUARD | PAGE_NOACCESS)) != 0) {
            return "---";
        }
        int base = protect & 0xff;
        boolean readable = base == PAGE_READONLY || base == PAGE_READWRITE || base == PAGE_WRITECOPY
                || base == PAGE_EXECUTE_READ || base == PAGE_EXECUTE_READWRITE || base == PAGE_EXECUTE_WRITECOPY;
        boolean writable = base == PAGE_READWRITE || base == PAGE_WRITECOPY
                || base == PAGE_EXECUTE_READWRITE || base == PAGE_EXECUTE_WRITECOPY;
        boolean executable = base == PAGE_EXECUTE || base == PAGE_EXECUTE_READ
                || base == PAGE_EXECUTE_READWRITE || base == PAGE_EXECUTE_WRITECOPY;
        return (readable ? "r" : "-") + (writable ? "w" : "-") + (executable ? "x" : "-");
    }

    private static IOException win32Error(String message) {
        return new IOException(message + ", Windows error " + Native.getLastError());
    }

    @Override
    public void close() throws IOException {
        if (!Kernel32.INSTANCE.CloseHandle(process)) {
            throw win32Error("CloseHandle failed for PID " + pid);
        }
    }

    private record ModuleRange(long start, long end, String path) {
    }

    @Structure.FieldOrder({
            "BaseAddress", "AllocationBase", "AllocationProtect", "alignment",
            "RegionSize", "State", "Protect", "Type", "alignment2"
    })
    public static class MemoryBasicInformation extends Structure {
        public Pointer BaseAddress;
        public Pointer AllocationBase;
        public int AllocationProtect;
        public int alignment;
        public long RegionSize;
        public int State;
        public int Protect;
        public int Type;
        public int alignment2;
    }

    @Structure.FieldOrder({
            "dwSize", "th32ModuleID", "th32ProcessID", "GlblcntUsage", "ProccntUsage",
            "modBaseAddr", "modBaseSize", "hModule", "szModule", "szExePath"
    })
    public static class ModuleEntry32 extends Structure {
        public int dwSize;
        public int th32ModuleID;
        public int th32ProcessID;
        public int GlblcntUsage;
        public int ProccntUsage;
        public Pointer modBaseAddr;
        public int modBaseSize;
        public Pointer hModule;
        public char[] szModule = new char[256];
        public char[] szExePath = new char[260];
    }

    @Structure.FieldOrder({"lpBaseOfDll", "SizeOfImage", "EntryPoint"})
    public static class ModuleInfo extends Structure {
        public Pointer lpBaseOfDll;
        public int SizeOfImage;
        public Pointer EntryPoint;
    }

    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer OpenProcess(int desiredAccess, boolean inheritHandle, int processId);

        boolean ReadProcessMemory(Pointer process, Pointer baseAddress, Pointer buffer, long size, LongByReference bytesRead);

        long VirtualQueryEx(Pointer process, Pointer address, MemoryBasicInformation buffer, long length);

        Pointer CreateToolhelp32Snapshot(int flags, int processId);

        boolean Module32FirstW(Pointer snapshot, ModuleEntry32 entry);

        boolean Module32NextW(Pointer snapshot, ModuleEntry32 entry);

        boolean CloseHandle(Pointer handle);

        boolean K32EnumProcessModulesEx(
                Pointer process, Pointer modules, int size, IntByReference needed, int filterFlag);

        int K32GetModuleFileNameExW(Pointer process, Pointer module, char[] filename, int size);

        boolean K32GetModuleInformation(Pointer process, Pointer module, ModuleInfo info, int size);
    }
}
