#!/bin/bash

# Script to tag and create GitHub release based on version in packageManifest.json
# Usage: ./tag-and-release.sh [--dry-run]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if dry-run mode
DRY_RUN=false
if [[ "$1" == "--dry-run" ]]; then
    DRY_RUN=true
    echo -e "${YELLOW}Running in dry-run mode (no changes will be made)${NC}"
fi

# Check if packageManifest.json exists
if [[ ! -f "packageManifest.json" ]]; then
    echo -e "${RED}Error: packageManifest.json not found${NC}"
    exit 1
fi

# Extract version from packageManifest.json
VERSION=$(grep -o '"version": *"[^"]*"' packageManifest.json | cut -d'"' -f4)

if [[ -z "$VERSION" ]]; then
    echo -e "${RED}Error: Could not extract version from packageManifest.json${NC}"
    exit 1
fi

echo -e "${GREEN}Found version: ${VERSION}${NC}"

# Check if git is available
if ! command -v git &> /dev/null; then
    echo -e "${RED}Error: git is not installed${NC}"
    exit 1
fi

# Check if we're in a git repository
if ! git rev-parse --git-dir > /dev/null 2>&1; then
    echo -e "${RED}Error: Not in a git repository${NC}"
    exit 1
fi

# Check if there are uncommitted changes
if [[ -n "$(git status --porcelain)" ]]; then
    echo -e "${YELLOW}Warning: You have uncommitted changes${NC}"
    read -p "Continue anyway? (y/N) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Check if tag already exists
TAG="v${VERSION}"
if git rev-parse "$TAG" >/dev/null 2>&1; then
    echo -e "${RED}Error: Tag ${TAG} already exists${NC}"
    exit 1
fi

# Get the latest commit hash
LATEST_COMMIT=$(git rev-parse HEAD)
echo -e "${GREEN}Latest commit: ${LATEST_COMMIT:0:7}${NC}"

# Extract release notes from packageManifest.json
# Try using jq if available (more reliable JSON parsing)
if command -v jq &> /dev/null; then
    RELEASE_NOTES=$(jq -r '.releaseNotes' packageManifest.json 2>/dev/null || echo "")
else
    # Fallback to grep/sed method
    # Extract the releaseNotes value, handling multi-line JSON
    RELEASE_NOTES=$(grep -o '"releaseNotes": *"[^"]*"' packageManifest.json | sed 's/"releaseNotes": *"\(.*\)"/\1/' | sed 's/\\n/\n/g')
fi

# If still empty or extraction failed, use default
if [[ -z "$RELEASE_NOTES" || "$RELEASE_NOTES" == "null" ]]; then
    RELEASE_NOTES="Release version ${VERSION}"
fi

echo -e "${GREEN}Release notes:${NC}"
echo "$RELEASE_NOTES"
echo

# Create tag
if [[ "$DRY_RUN" == "true" ]]; then
    echo -e "${YELLOW}[DRY RUN] Would create tag: ${TAG}${NC}"
    echo -e "${YELLOW}[DRY RUN] Tag message: Release version ${VERSION}${NC}"
else
    echo -e "${GREEN}Creating tag: ${TAG}${NC}"
    git tag -a "$TAG" -m "Release version ${VERSION}"
    echo -e "${GREEN}Tag created successfully${NC}"
fi

# Push tag to remote
REMOTE=$(git remote | head -n 1)
if [[ -z "$REMOTE" ]]; then
    echo -e "${YELLOW}Warning: No remote repository found. Tag created locally only.${NC}"
else
    if [[ "$DRY_RUN" == "true" ]]; then
        echo -e "${YELLOW}[DRY RUN] Would push tag to ${REMOTE}${NC}"
    else
        echo -e "${GREEN}Pushing tag to ${REMOTE}...${NC}"
        git push "$REMOTE" "$TAG"
        echo -e "${GREEN}Tag pushed successfully${NC}"
    fi
fi

# Create GitHub release
if command -v gh &> /dev/null; then
    if [[ "$DRY_RUN" == "true" ]]; then
        echo -e "${YELLOW}[DRY RUN] Would create GitHub release: ${TAG}${NC}"
        echo -e "${YELLOW}[DRY RUN] Release title: Version ${VERSION}${NC}"
    else
        echo -e "${GREEN}Creating GitHub release...${NC}"
        # Create a temporary file for release notes to ensure proper handling
        TEMP_NOTES=$(mktemp)
        echo -e "$RELEASE_NOTES" > "$TEMP_NOTES"
        gh release create "$TAG" \
            --title "Version ${VERSION}" \
            --notes-file "$TEMP_NOTES"
        rm -f "$TEMP_NOTES"
        echo -e "${GREEN}GitHub release created successfully${NC}"
    fi
else
    echo -e "${YELLOW}GitHub CLI (gh) not found. Skipping GitHub release creation.${NC}"
    echo -e "${YELLOW}To create a release manually:${NC}"
    echo -e "  1. Go to: https://github.com/$(git config --get remote.origin.url | sed 's/.*github.com[:/]\([^.]*\).*/\1/')/releases/new"
    echo -e "  2. Select tag: ${TAG}"
    echo -e "  3. Title: Version ${VERSION}"
    echo -e "  4. Description:"
    echo "$RELEASE_NOTES" | sed 's/^/     /'
fi

echo
echo -e "${GREEN}✓ Tag and release process completed!${NC}"
if [[ "$DRY_RUN" == "false" ]]; then
    echo -e "${GREEN}Tag: ${TAG}${NC}"
    echo -e "${GREEN}Commit: ${LATEST_COMMIT:0:7}${NC}"
fi

