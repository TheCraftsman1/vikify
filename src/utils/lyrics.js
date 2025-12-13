/**
 * Parse LRC format lyrics into array of objects
 * @param {string} lrcString - Raw LRC string
 * @returns {Array} - [{ time: number (seconds), text: string }]
 */
export const parseLRC = (lrcString) => {
    if (!lrcString) return [];

    const lines = lrcString.split('\n');
    const result = [];
    const timeRegex = /\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?\]/;

    for (const line of lines) {
        const match = timeRegex.exec(line);
        if (match) {
            const minutes = parseInt(match[1], 10);
            const seconds = parseInt(match[2], 10);
            const milliseconds = match[3] ? parseInt(match[3].length === 2 ? match[3] + '0' : match[3], 10) : 0;

            const time = minutes * 60 + seconds + milliseconds / 1000;
            const text = line.replace(timeRegex, '').trim();

            if (text) {
                result.push({ time, text });
            }
        }
    }

    return result;
};
