// Simple global function to show the about modal
function showAboutModal() {
  // Wait for the DOM to be fully loaded
  if (document.readyState !== 'loading') {
    initModal();
  } else {
    document.addEventListener('DOMContentLoaded', initModal);
  }
}

function initModal() {
  // Get or create the modal element
  let modal = document.getElementById('aboutModal');
  
  if (!modal) {
    // Create the modal HTML if it doesn't exist
    modal = document.createElement('div');
    modal.id = 'aboutModal';
    modal.style.cssText = `
      display: none;
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: rgba(0,0,0,0.5);
      z-index: 9999;
      overflow-y: auto;
      padding: 20px;
      justify-content: center;
      align-items: flex-start;
    `;
    
    modal.innerHTML = `
      <div style="max-width: 800px; margin: 2rem auto; background: white; padding: 1.5rem; border-radius: 8px;">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
          <h3 style="margin: 0;">About</h3>
          <button id="closeAboutModal" style="background: none; border: none; font-size: 1.5rem; cursor: pointer;">&times;</button>
        </div>
        <div>
          <div id="aboutLoading" style="text-align: center; padding: 2rem 0;">
            <div class="spinner-border text-primary" role="status">
              <span class="visually-hidden">Loading...</span>
            </div>
            <p class="mt-2">Loading release notes...</p>
          </div>
          <div id="aboutContent" style="display: none;"></div>
        </div>
      </div>
    `;
    
    document.body.appendChild(modal);
    
    // Add close handler
    document.getElementById('closeAboutModal').addEventListener('click', function() {
      modal.style.display = 'none';
    });
    
    // Close when clicking outside content
    modal.addEventListener('click', function(e) {
      if (e.target === modal) {
        modal.style.display = 'none';
      }
    });
  }

  // Show the modal
  modal.style.display = 'flex';
  
  // Get or create the content elements
  const loadingEl = document.getElementById('aboutLoading');
  const contentEl = document.getElementById('aboutContent');
  
  if (loadingEl && contentEl) {
    loadingEl.style.display = 'block';
    contentEl.style.display = 'none';
    
    // Load the release notes
    fetch('/RELEASENOTES.md')
      .then(response => {
        if (!response.ok) throw new Error('Network response was not ok');
        return response.text();
      })
      .then(text => {
        if (window.marked) {
          contentEl.innerHTML = window.marked.parse(text);
        } else {
          contentEl.textContent = text;
        }
        loadingEl.style.display = 'none';
        contentEl.style.display = 'block';
      })
      .catch(error => {
        console.error('Error loading release notes:', error);
        if (loadingEl) {
          loadingEl.innerHTML = '<p>Failed to load release notes. Please try again later.</p>';
        }
      });
  }
}

// Make the function globally available
window.showAboutModal = showAboutModal;
