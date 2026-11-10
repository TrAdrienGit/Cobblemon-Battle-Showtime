#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Time;
uniform float Amount;

in vec2 texCoord;

out vec4 fragColor;

float noise(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec3 color = texture(DiffuseSampler, texCoord).rgb;
    float grey = dot(color, vec3(0.299, 0.587, 0.114));
    grey = clamp((grey - 0.5) * 1.12 + 0.5, 0.0, 1.0);
    float frame = floor(Time * 24.0);
    float grain = noise(floor(texCoord * OutSize / 1.5) + frame * 17.31) - 0.5;
    grey += grain * 0.07 * Amount;
    vec2 centred = texCoord - 0.5;
    float rim = 1.0 - smoothstep(0.25, 0.85, length(centred * vec2(1.0, 0.8)) * 1.25);
    grey *= mix(1.0, rim, 0.6 * Amount);
    fragColor = vec4(vec3(grey), 1.0);
}
