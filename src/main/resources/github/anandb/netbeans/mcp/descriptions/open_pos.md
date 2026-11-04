Opens a file at the specified line number in the editor. Only works for files within the current project (files outside the project are rejected).

Two modes, picked with the "focus" argument:

- focus=false (default) - background navigation. The editor opens at that line, but focus is not taken, so the user keeps working where they are. Use this for every navigation YOU initiate while reading, searching, or writing files. It opens a tab; never use it to get the user's attention.
- focus=true - takes the user there: the editor is activated and focused. Use ONLY when the user explicitly asked to be taken to a location ('show me where X happens', 'take me to line 42', 'open X at line 10', 'jump to the error').

Use when the user wants to:
- Navigate to a specific location in the code
- See where something is defined or used
- Jump to an error, warning, or referenced location
- Explore code structure visually

Trigger phrases for focus=true:

# 1. Open or navigate to code
- "Open it in the editor"
- "Open the file at line X"
- "Jump to..."
- "Take me to..."
- "Navigate to..."
- "Go to the definition of..."

# 2. Locate implementations or references
- "Where is this defined?"
- "Where is this implemented?"
- "Where is this called?"
- "Where else is this used?"
- "Find all references to..."
- "Where do we use this pattern?"

# 3. Inspect specific code
- "Show me where this happens"
- "Show me the code for..."
- "Show me the implementation of..."
- "Take me to the function that handles..."
- "Open the code responsible for..."

# 4. Find code related to an issue
- "Where is this bug occurring?"
- "Show me where this is handled"
- "Find the code responsible for..."
- "Where does this error originate?"

Behavior:
- Open the relevant file in the editor.
- Focus the cursor on the corresponding code location.
- If multiple locations are relevant, navigate to the most relevant match.
- Do not activate focus=true for general code explanations or modification requests.
Examples:
- 'Show me the login method in AuthController' -> filePath='.../AuthController.java', line=42
- 'Open pom.xml at line 15' -> filePath='.../pom.xml', line=15
- 'Where is ProcessManager used?' -> nb_open_pos at each call site
