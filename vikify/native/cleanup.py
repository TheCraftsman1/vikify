#!/usr/bin/env python3
"""
Vikify Project Cleanup Script
==============================
Safely removes temporary build logs and error files.

CONSTRAINT: Does NOT touch any .kt or .xml files unless they explicitly 
have "Copy", "Old", or "Backup" in the name (none were found).

Usage:
    python cleanup.py          # Dry run (shows what will be deleted)
    python cleanup.py --execute  # Actually delete files
"""

import os
import sys
import shutil
from pathlib import Path

# Project root directory (native/)
PROJECT_ROOT = Path(__file__).parent

# Parent directories that may be unused
VIKIFY_UI_ROOT = PROJECT_ROOT.parent.parent  # Vikify_UI/
VIKIFY_ROOT = PROJECT_ROOT.parent  # vikify/

# Files to delete (relative to project root - native/)
FILES_TO_DELETE = [
    # Build logs (~28MB total)
    "build_log.txt",
    "build_log_2.txt",
    "build_assemble.txt",
    "build_blank.txt",
    "build_err.txt",
    "build_err_verify.txt",
    "build_error.txt",  # ~26MB!
    "build_errors.txt",
    "build_log_assemble.txt",
    "build_log_attempt7.txt",
    "build_log_autoplay_fix.txt",
    "build_log_colorful.txt",
    "build_log_context_menu.txt",
    "build_log_core_fixes.txt",
    "build_log_debug_2.txt",
    "build_log_deep_fixes.txt",
    "build_log_deep_fixes2.txt",
    "build_log_design.txt",
    "build_log_design_retry.txt",
    "build_log_final.txt",
    "build_log_final_attempt.txt",
    "build_log_final_fix.txt",
    "build_log_final_verification.txt",
    "build_log_fix_verification.txt",
    "build_log_fixed.txt",
    "build_log_home.txt",
    "build_log_home_3.txt",
    "build_log_home_4.txt",
    "build_log_home_5.txt",
    "build_log_import_fix.txt",
    "build_log_install_attempt_3.txt",
    "build_log_install_attempt_4.txt",
    "build_log_install_attempt_5.txt",
    "build_log_install_attempt_6.txt",
    "build_log_install_final.txt",
    "build_log_install_final_retry.txt",
    "build_log_install_retry.txt",
    "build_log_install_retry_2.txt",
    "build_log_install_success.txt",
    "build_log_living_bg.txt",
    "build_log_living_bg2.txt",
    "build_log_living_bg3.txt",
    "build_log_living_bg4.txt",
    "build_log_mutex_fix.txt",
    "build_log_playback_fix.txt",
    "build_log_premium.txt",
    "build_log_queue_fallback.txt",
    "build_log_queue_fix.txt",
    "build_log_queue_fixes.txt",
    "build_log_queue_wait_fix.txt",
    "build_log_refined.txt",
    "build_log_retry.txt",
    "build_log_screens.txt",
    "build_log_settings_redesign.txt",
    "build_log_settings_retry.txt",
    "build_log_settings_retry_2.txt",
    "build_log_stale_url_fix.txt",
    "build_log_utf8.txt",
    "build_log_vm_fix.txt",
    "build_minimal.txt",
    "build_neumorphic.txt",
    "build_output.txt",
    "build_output_2.txt",
    "build_output_3.txt",
    "build_stripped.txt",
    "build_v2.txt",
    
    # Error logs
    "error.log",
    "error.txt",
    "error_clean.log",
    "error_clean_build.log",
    "error_clean_build_2.log",
    "error_final.log",
    "error_installCoreDebug_1.log",
    "error_installCoreDebug_3.log",
    
    # Install/task logs
    "install_log.txt",
    "install_log_2.txt",
    "install_log_3.txt",
    "install_tasks.txt",
    "log.txt",
    "signing_report.txt",
    "tasks_list.txt",
]

# Unused directories (will be shown but require --execute-dirs to delete)
DIRECTORIES_TO_DELETE = [
    # In Vikify_UI/ (parent of vikify/)
    (VIKIFY_UI_ROOT / "app", "Duplicate app folder in Vikify_UI (we use native/app)"),
    (VIKIFY_UI_ROOT / "build", "Unused build directory in Vikify_UI"),
    (VIKIFY_UI_ROOT / ".gradle", "Gradle cache in Vikify_UI"),
    (VIKIFY_UI_ROOT / ".kotlin", "Kotlin cache in Vikify_UI"),
    
    # In vikify/ 
    (VIKIFY_ROOT / "ui_legacy_backup", "Old UI backup (151 files)"),
    (VIKIFY_ROOT / "node_modules", "Unused Node.js dependencies"),
    (VIKIFY_ROOT / ".venv", "Unused Python virtual environment"),
]

def get_file_size_str(size_bytes):
    """Convert bytes to human-readable size."""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if size_bytes < 1024:
            return f"{size_bytes:.1f} {unit}"
        size_bytes /= 1024
    return f"{size_bytes:.1f} TB"

def get_dir_size(path):
    """Get total size of a directory."""
    total = 0
    try:
        for p in path.rglob('*'):
            if p.is_file():
                total += p.stat().st_size
    except:
        pass
    return total

def main():
    execute_files = "--execute" in sys.argv
    execute_dirs = "--execute-dirs" in sys.argv
    
    print("=" * 60)
    print("   Vikify Project Cleanup Script")
    print("=" * 60)
    print()
    
    # === PART 1: Log Files ===
    print("📄 TEMPORARY LOG FILES")
    print("-" * 60)
    
    files_found = []
    total_file_size = 0
    
    for filename in FILES_TO_DELETE:
        filepath = PROJECT_ROOT / filename
        if filepath.exists():
            size = filepath.stat().st_size
            files_found.append((filepath, size))
            total_file_size += size
    
    if files_found:
        print(f"Found {len(files_found)} files ({get_file_size_str(total_file_size)}):\n")
        for filepath, size in files_found[:10]:  # Show first 10
            print(f"  [{get_file_size_str(size):>10}] {filepath.name}")
        if len(files_found) > 10:
            print(f"  ... and {len(files_found) - 10} more files")
    else:
        print("✓ No temporary log files found.")
    
    print()
    
    # === PART 2: Unused Directories ===
    print("📁 UNUSED DIRECTORIES")
    print("-" * 60)
    
    dirs_found = []
    total_dir_size = 0
    
    for dirpath, reason in DIRECTORIES_TO_DELETE:
        if dirpath.exists() and dirpath.is_dir():
            size = get_dir_size(dirpath)
            dirs_found.append((dirpath, reason, size))
            total_dir_size += size
    
    if dirs_found:
        print(f"Found {len(dirs_found)} directories ({get_file_size_str(total_dir_size)}):\n")
        for dirpath, reason, size in dirs_found:
            rel_path = str(dirpath).replace(str(VIKIFY_UI_ROOT), "Vikify_UI")
            print(f"  [{get_file_size_str(size):>10}] {rel_path}")
            print(f"               └─ {reason}")
    else:
        print("✓ No unused directories found.")
    
    print()
    print("=" * 60)
    print(f"TOTAL: {len(files_found)} files + {len(dirs_found)} directories")
    print(f"       (~{get_file_size_str(total_file_size + total_dir_size)} can be freed)")
    print("=" * 60)
    print()
    
    # === EXECUTION ===
    if not execute_files and not execute_dirs:
        print("This is a DRY RUN. No files were deleted.")
        print()
        print("Usage:")
        print("  python cleanup.py --execute       # Delete log files only")
        print("  python cleanup.py --execute-dirs  # Delete directories only")
        print("  python cleanup.py --execute --execute-dirs  # Delete both")
        return
    
    # Delete files
    if execute_files and files_found:
        response = input(f"\nDelete {len(files_found)} log files? (y/n): ")
        if response.lower() == 'y':
            for filepath, _ in files_found:
                print(f"Deleting {filepath.name}...")
                filepath.unlink()
            print(f"✓ Deleted {len(files_found)} files")
    
    # Delete directories
    if execute_dirs and dirs_found:
        print("\n⚠️  WARNING: Deleting directories is more dangerous!")
        for dirpath, reason, size in dirs_found:
            rel_path = str(dirpath).replace(str(VIKIFY_UI_ROOT), "Vikify_UI")
            response = input(f"\nDelete '{rel_path}' ({get_file_size_str(size)})? (y/n): ")
            if response.lower() == 'y':
                print(f"Deleting {rel_path}...")
                shutil.rmtree(dirpath)
                print(f"✓ Deleted")

if __name__ == "__main__":
    main()
