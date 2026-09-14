# KLYNT Dangerfile
# Run with: `danger` via CI or locally

# Warn on large PRs
warn_on_prs = ->(pr) {
  pr.add_to_project_size! if pr.source_branch.commits.count > 20
}

# Check for TODO/FIXME in PRs
github.pr_diff.each_line do |line|
  if line.include?("TODO") || line.include?("FIXME")
    warn("Found TODO/FIXME in PR: #{line.strip}")
  end
end

# Validate that version bumps are always accompanied by CHANGELOG updates
if github.pr_title.include?("bump") && !github.pr_body.include?("CHANGELOG")
  warn("Version bump detected without CHANGELOG update")
end

# Flag if apple trademark appears
if github.pr_diff.include?("apple") && !github.pr_body.include?("platform")
  warn("Possible 'apple' trademark in PR — use platform-neutral language")
end
