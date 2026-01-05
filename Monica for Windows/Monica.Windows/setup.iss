; Monica for Windows Installer Script
; Inno Setup Script

#define MyAppName "Monica"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Monica Team"
#define MyAppExeName "Monica.Windows.exe"
#define MyAppURL "https://github.com/JoyinJoester/Monica"

[Setup]
; Basic info
AppId={{B8C7D5A2-4F3E-4A1B-9C6D-8E2F1A3B5C7D}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
; Output settings
OutputDir=..\..\..\installer
OutputBaseFilename=Monica_Setup_{#MyAppVersion}_x64
SetupIconFile=AppIcon.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
; Require admin for Program Files
PrivilegesRequired=admin
; x64 only
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
; Minimum Windows version (Windows 10 1809+)
MinVersion=10.0.17763

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Main application files
Source: "bin\x64\Release\net8.0-windows10.0.19041.0\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
// Check for Windows App Runtime - optional, since app is self-contained
function InitializeSetup(): Boolean;
begin
  Result := True;
end;
