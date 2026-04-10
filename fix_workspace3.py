with open("src/main/resources/templates/workspace.html", "r", encoding="utf-8") as f:
    text = f.read()

# Change class="step-content" to class="content-body"
text = text.replace('class="step-content"', 'class="content-body"')

# Move bottom-action-bar AFTER step3-content
# Action bar is:
#         <!-- Action Bar -->
#         <footer class="bottom-action-bar">
# ...
#         </footer>
# 
# Step 3 is:
#         <div id="step3-content" class="content-body" style="display: none;" x-data="workspaceStep3()">
# ... everything until next </main>

# Let's extract the action bar
start_action_bar = text.find("        <!-- Action Bar -->")
end_action_bar = text.find("        </footer>\n") + len("        </footer>\n")

action_bar_str = text[start_action_bar:end_action_bar]

# Remove it from its current position
text = text[:start_action_bar] + text[end_action_bar:]

# Now insert it right before </main>
end_main_pos = text.find("    </main>")
text = text[:end_main_pos] + action_bar_str + "    </main>\n" + text[end_main_pos + len("    </main>"): ]

with open("src/main/resources/templates/workspace.html", "w", encoding="utf-8") as f:
    f.write(text)

print("Moved Action bar to the end!")
