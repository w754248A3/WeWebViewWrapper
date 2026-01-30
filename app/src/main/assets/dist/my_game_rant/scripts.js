


function createArticleElement(title, content, createTime, updateTime) {
    // 创建 article 容器 div
    const articleDiv = document.createElement('div');
    articleDiv.className = 'article';

    // 创建并设置文章标题和时间信息的容器
    const articleHeaderDiv = document.createElement('div');
    articleHeaderDiv.className = 'article-header';

    const titleElement = document.createElement('h2');
    titleElement.textContent = title;

    const createTimeElement = document.createElement('p');
    createTimeElement.textContent = `创建时间: ${createTime}`;

    const updateTimeElement = document.createElement('p');
    updateTimeElement.textContent = `修改时间: ${updateTime}`;

    // 将标题和时间信息添加到 articleHeaderDiv
    articleHeaderDiv.appendChild(titleElement);
    articleHeaderDiv.appendChild(createTimeElement);
    articleHeaderDiv.appendChild(updateTimeElement);

    // 创建并设置文章内容的 div
    const articleContentDiv = document.createElement('div');
    articleContentDiv.className = 'article-content';

    // 处理文章内容，显示前20个字符，剩下的部分存储在 data-full-content 属性中
    const truncatedContent = content.slice(0, 20) + '...';
    articleContentDiv.textContent = truncatedContent;
    articleContentDiv.setAttribute('data-full-content', content);

    // 创建并设置 "显示全部" 按钮
    const toggleButton = document.createElement('button');
    toggleButton.className = 'toggle-content';
    toggleButton.textContent = '显示全部';

    // 将所有子元素添加到 articleDiv
    articleDiv.appendChild(articleHeaderDiv);
    articleDiv.appendChild(articleContentDiv);
    articleDiv.appendChild(toggleButton);

    return articleDiv;
}

const articleList = document.getElementById('articleList');
function addArticle(title, content, createTime, updateTime) {
    const articleElement = createArticleElement(title, content, createTime, updateTime);
    articleList.appendChild(articleElement);
}

function encodeToBase64FromUtf8(str) {
    // 将字符串转换为 UTF-8 编码的字节数组
    const utf8Bytes = new TextEncoder().encode(str);
    
    // 将字节数组转换为 Base64 编码的字符串
    let binaryString = '';
    utf8Bytes.forEach(byte => {
        binaryString += String.fromCharCode(byte);
    });

    return btoa(binaryString);
}


function decodeFromBase64ToUtf8(base64Str) {
    // 将 Base64 编码的字符串解码为二进制字符串
    const binaryString = atob(base64Str);

    // 将二进制字符串转换为字节数组
    const utf8Bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
        utf8Bytes[i] = binaryString.charCodeAt(i);
    }

    // 将字节数组转换为 UTF-8 编码的字符串
    return new TextDecoder().decode(utf8Bytes);
}


fetch('./result.json')
    .then(response => response.json())
    .then(data => {

        console.log(data);

        for (const element of data) {

            addArticle(decodeFromBase64ToUtf8(element["fileNameBase64"]),
                decodeFromBase64ToUtf8(element["contentBase64"]),
                element["creationTime"],
                element["modificationTime"]);


        }



        document.querySelectorAll('.toggle-content').forEach(button => {
            button.addEventListener('click', function() {
                const articleContent = this.previousElementSibling;
                const isExpanded = articleContent.classList.toggle('expanded');
        
                if (isExpanded) {
                    articleContent.textContent = articleContent.getAttribute('data-full-content');
                    this.textContent = '折叠';
                } else {
                    articleContent.textContent = articleContent.getAttribute('data-full-content').slice(0, 20) + '...';
                    this.textContent = '显示全部';
                }
            });
        });
        

    });
