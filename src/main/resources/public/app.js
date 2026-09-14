function showLoading() {
  document.getElementById('result').textContent = 'Loading...';
  document.getElementById('error').textContent = '';
}

function showResult(text) {
  document.getElementById('result').textContent = text;
  document.getElementById('error').textContent = '';
}

function showError(text) {
  document.getElementById('result').textContent = '';
  document.getElementById('error').textContent = text;
}

async function callService(url) {
  showLoading();
  try {
    const response = await fetch(url);
    if (!response.ok) {
      const problem = await response.json().catch(() => ({}));
      showError(problem.error || ('Request failed with status ' + response.status));
      return null;
    }
    return await response.json();
  } catch (networkError) {
    showError('Network error: could not reach the server.');
    return null;
  }
}

document.getElementById('greet-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const name = document.getElementById('name-input').value;
  const data = await callService('/api/greet?name=' + encodeURIComponent(name));
  if (data) showResult(data.message);
});

document.getElementById('square-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const value = document.getElementById('value-input').value;
  const data = await callService('/api/square?value=' + encodeURIComponent(value));
  if (data) showResult(data.input + ' squared is ' + data.square);
});

document.getElementById('time-btn').addEventListener('click', async () => {
  const data = await callService('/api/time');
  if (data) showResult('Server time: ' + data.serverTime);
});