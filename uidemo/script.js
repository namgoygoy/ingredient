document.addEventListener('DOMContentLoaded', () => {
    const navButtons = document.querySelectorAll('.nav-btn');
    const screens = document.querySelectorAll('.screen');
    const captureBtn = document.getElementById('capture-btn');

    function showScreen(screenId) {
        // (중요) 1. 모든 스크린을 숨기기 위해 'active' 클래스 제거
        screens.forEach(screen => {
            screen.classList.remove('active');
        });

        // (중요) 2. 모든 네비게이션 버튼의 활성 상태 제거
        navButtons.forEach(btn => {
            btn.classList.remove('active');
        });

        // (중요) 3. 요청된 스크린(ID)을 찾아 'active' 클래스 추가
        const activeScreen = document.getElementById(screenId);
        if (activeScreen) {
            activeScreen.classList.add('active');
        }

        // (중요) 4. 요청된 스크린에 맞는 네비게이션 버튼을 찾아 'active' 클래스 추가
        const activeButton = document.querySelector(`.nav-btn[data-screen="${screenId}"]`);
        if (activeButton) {
            activeButton.classList.add('active');
        }
    }

    // 네비게이션 버튼 클릭 이벤트
    navButtons.forEach(button => {
        button.addEventListener('click', () => {
            const screenId = button.dataset.screen;
            showScreen(screenId);
        });
    });

    // "촬영하기" 버튼 클릭 시 -> '결과' 화면으로 이동
    captureBtn.addEventListener('click', () => {
        showScreen('results-screen');
    });

    // 초기 로드: 스캔 화면 보여주기
    showScreen('scan-screen'); 
});