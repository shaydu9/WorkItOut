#!/bin/bash

echo "🔐 Setting up SSH for GitHub..."
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if SSH key already exists
if [ -f ~/.ssh/id_ed25519 ]; then
    echo -e "${YELLOW}⚠️  SSH key already exists at ~/.ssh/id_ed25519${NC}"
    echo "Using existing key..."
else
    echo "📝 Creating new SSH key..."
    ssh-keygen -t ed25519 -C "shaydu9@github" -f ~/.ssh/id_ed25519 -N ""
    echo -e "${GREEN}✅ SSH key created!${NC}"
fi

echo ""
echo "🔧 Starting SSH agent..."
eval "$(ssh-agent -s)"

echo ""
echo "🔑 Adding SSH key to agent..."
ssh-add ~/.ssh/id_ed25519

echo ""
echo "📋 Your SSH public key:"
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"
cat ~/.ssh/id_ed25519.pub
echo -e "${BLUE}═══════════════════════════════════════════════════════${NC}"

# Copy to clipboard if pbcopy is available (macOS)
if command -v pbcopy &> /dev/null; then
    cat ~/.ssh/id_ed25519.pub | pbcopy
    echo -e "${GREEN}✅ SSH key copied to clipboard!${NC}"
fi

echo ""
echo -e "${YELLOW}📌 NEXT STEPS:${NC}"
echo "1. The SSH key has been copied to your clipboard"
echo "2. Go to: ${BLUE}https://github.com/settings/ssh/new${NC}"
echo "3. Title: 'WorkItOut Mac'"
echo "4. Paste the key (Cmd+V)"
echo "5. Click 'Add SSH key'"
echo ""
echo "Press ENTER after you've added the key to GitHub..."
read

echo ""
echo "🔄 Updating git remote to use SSH..."
cd /Users/shaydubrovsky/Projects/WorkItOut
git remote set-url origin git@github.com:shaydu9/WorkItOut.git

echo ""
echo "🧪 Testing SSH connection to GitHub..."
ssh -T git@github.com

echo ""
echo "🚀 Pushing to GitHub..."
git push -u origin main

echo ""
echo -e "${GREEN}✅ Done! Your project is now on GitHub!${NC}"
echo -e "View it at: ${BLUE}https://github.com/shaydu9/WorkItOut${NC}"
